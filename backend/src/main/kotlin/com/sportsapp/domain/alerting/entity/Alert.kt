package com.sportsapp.domain.alerting.entity

import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.exception.InvalidAlertStateException
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
import java.time.ZonedDateTime
import org.hibernate.annotations.Type

/**
 * 지능형 장애 알림 1건의 상태·원지표를 캡슐화하는 Aggregate Root (TDD.md §Detail Design).
 *
 * [LimitedDrop][com.sportsapp.domain.goods.entity.LimitedDrop]과 동일하게 `private constructor` +
 * `create`/`reconstitute` 팩토리 패턴을 따른다. JPA Entity와 domain Entity를 분리하지 않는 이
 * 코드베이스의 기존 관행(Booking·LimitedDrop·Notification 선례)을 그대로 따른다.
 *
 * LLM 원인분석은 사용하지 않는다 — 알림 본문의 "원인"은 [TelemetryQueryGateway]가 조회한
 * Prometheus/Loki/Tempo 원지표([TelemetrySnapshot])를 그대로 렌더링한 결과다.
 */
@Entity
@Table(name = "alerts")
class Alert private constructor(
    @Column(name = "signal_key", nullable = false)
    val signalKey: String,

    @Column(name = "endpoint", nullable = false)
    val endpoint: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    val source: AlertSource,

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    val severity: AlertSeverity,

    @Column(name = "env", nullable = false, length = 20)
    val env: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private var status: AlertStatus,

    @Type(JsonStringType::class)
    @Column(name = "telemetry", columnDefinition = "TEXT")
    private var telemetry: TelemetrySnapshot?,

    @Column(name = "raised_at", nullable = false)
    val raisedAt: ZonedDateTime,

    @Column(name = "delivered_at")
    private var deliveredAt: ZonedDateTime?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    val currentStatus: AlertStatus get() = status
    val currentTelemetry: TelemetrySnapshot? get() = telemetry
    val deliveredAtValue: ZonedDateTime? get() = deliveredAt

    /**
     * 저장 직후(실 id 확보 후) 호출한다 — [create]는 아직 id가 없어 이벤트에 alertId를 담을 수 없으므로,
     * [com.sportsapp.domain.alerting.service.AlertDomainService.raise]가 repository.save 이후에 호출한다.
     */
    fun requestProcessing() {
        registerEvent(AlertProcessingRequestedEvent(alertId = id))
    }

    /**
     * Prometheus/Loki/Tempo에서 조회한 원지표 스냅샷을 부착한다. LLM 원인분석을 쓰지 않으므로
     * 조회 결과와 무관하게 항상 ENRICHED로 전이하고 발송 이벤트를 등록한다 —
     * [com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway]는 소스별 부분 실패를 흡수해
     * 예외를 던지지 않으므로 폴백 분기가 필요 없다.
     */
    fun attachTelemetry(snapshot: TelemetrySnapshot) {
        transitTo(AlertStatus.ENRICHED)
        this.telemetry = snapshot
        registerEvent(
            AlertDeliveryReadyEvent(
                alertId = id,
                title = buildDeliveryTitle(),
                body = buildDeliveryBody(snapshot),
                source = source,
                severity = severity,
                env = env,
            )
        )
    }

    /** 발송 성공 — DELIVERED로 전이하고 발송 시각을 기록한다. */
    fun markDelivered() {
        transitTo(AlertStatus.DELIVERED)
        this.deliveredAt = ZonedDateTime.now()
    }

    /** 발송 실패 — DELIVERY_FAILED로 전이한다. */
    fun markDeliveryFailed() {
        transitTo(AlertStatus.DELIVERY_FAILED)
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    private fun transitTo(next: AlertStatus) {
        if (!status.canTransitTo(next)) throw InvalidAlertStateException(status, next)
        status = next
    }

    private fun buildDeliveryTitle(): String = "[$severity] $source 알림 — $endpoint"

    private fun buildDeliveryBody(snapshot: TelemetrySnapshot): String {
        if (snapshot.isEmpty) return TELEMETRY_UNAVAILABLE_BODY
        val sections = buildList {
            if (snapshot.metricsSummary.isNotBlank()) add("- 메트릭: ${truncateSample(snapshot.metricsSummary)}")
            if (snapshot.logSamples.isNotEmpty()) add(buildSampleSection("로그", snapshot.logSamples))
            if (snapshot.traceSamples.isNotEmpty()) add(buildSampleSection("trace", snapshot.traceSamples))
        }
        return "원인(원지표):\n" + sections.joinToString("\n")
    }

    private fun buildSampleSection(label: String, samples: List<String>): String {
        val shown = samples.take(MAX_SAMPLES_IN_BODY)
        val remaining = samples.size - shown.size
        val lines = shown.map { "  ${truncateSample(it)}" } +
            if (remaining > 0) listOf("  외 ${remaining}건") else emptyList()
        return "- $label(${samples.size}건):\n" + lines.joinToString("\n")
    }

    /** 단일 샘플이 지나치게 길면 절단한다 — Discord 메시지 길이 한계(embed 4096자) 방어. */
    private fun truncateSample(sample: String): String =
        if (sample.length > MAX_SAMPLE_LENGTH) sample.take(MAX_SAMPLE_LENGTH) + "…" else sample

    companion object {
        private const val MAX_SAMPLES_IN_BODY = 3
        private const val MAX_SAMPLE_LENGTH = 500
        private const val TELEMETRY_UNAVAILABLE_BODY = "원인: 원지표를 조회하지 못했습니다(데이터 없음 또는 조회 실패)."

        /** 신규 알림 생성(RAISED) — 쿨다운을 획득했을 때만 호출된다. */
        fun create(signal: AlertSignal, env: String): Alert = Alert(
            signalKey = signal.cooldownKey(env),
            endpoint = signal.endpoint,
            source = signal.source,
            severity = signal.severity,
            env = env,
            status = AlertStatus.RAISED,
            telemetry = null,
            raisedAt = ZonedDateTime.now(),
            deliveredAt = null,
        )

        /** 영속화 계층 복원 — 검증 없이 필드를 그대로 복구한다. */
        fun reconstitute(
            signalKey: String,
            endpoint: String,
            source: AlertSource,
            severity: AlertSeverity,
            env: String,
            status: AlertStatus,
            telemetry: TelemetrySnapshot?,
            raisedAt: ZonedDateTime,
            deliveredAt: ZonedDateTime?,
        ): Alert = Alert(
            signalKey = signalKey,
            endpoint = endpoint,
            source = source,
            severity = severity,
            env = env,
            status = status,
            telemetry = telemetry,
            raisedAt = raisedAt,
            deliveredAt = deliveredAt,
        )
    }
}
