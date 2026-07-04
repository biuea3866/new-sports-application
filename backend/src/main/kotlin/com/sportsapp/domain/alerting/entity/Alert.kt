package com.sportsapp.domain.alerting.entity

import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.event.AlertProcessingRequestedEvent
import com.sportsapp.domain.alerting.exception.InvalidAlertStateException
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.IncidentAnalysis
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
 * 지능형 장애 알림 1건의 상태·분석 결과를 캡슐화하는 Aggregate Root (TDD.md §Detail Design).
 *
 * [LimitedDrop][com.sportsapp.domain.goods.entity.LimitedDrop]과 동일하게 `private constructor` +
 * `create`/`reconstitute` 팩토리 패턴을 따른다. JPA Entity와 domain Entity를 분리하지 않는 이
 * 코드베이스의 기존 관행(Booking·LimitedDrop·Notification 선례)을 그대로 따른다.
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
    @Column(name = "analysis", columnDefinition = "TEXT")
    private var analysis: IncidentAnalysis?,

    @Column(name = "analysis_included")
    private var analysisIncluded: Boolean?,

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
    val currentAnalysis: IncidentAnalysis? get() = analysis
    val currentAnalysisIncluded: Boolean? get() = analysisIncluded
    val deliveredAtValue: ZonedDateTime? get() = deliveredAt

    /**
     * 저장 직후(실 id 확보 후) 호출한다 — [create]는 아직 id가 없어 이벤트에 alertId를 담을 수 없으므로,
     * [com.sportsapp.domain.alerting.service.AlertDomainService.raise]가 repository.save 이후에 호출한다.
     */
    fun requestProcessing() {
        registerEvent(AlertProcessingRequestedEvent(alertId = id))
    }

    /**
     * LLM 원인분석(또는 폴백) 결과를 부착한다. [analysis]의 [IncidentAnalysis.included] 여부로
     * ANALYZED/FALLBACK을 판정하고, 두 경우 모두 발송 이벤트를 등록한다(FR-8 — 폴백이어도 발송은 진행).
     */
    fun attachAnalysis(analysis: IncidentAnalysis) {
        val nextStatus = if (analysis.included) AlertStatus.ANALYZED else AlertStatus.FALLBACK
        transitTo(nextStatus)
        this.analysis = analysis
        this.analysisIncluded = analysis.included
        registerEvent(
            AlertDeliveryReadyEvent(
                alertId = id,
                title = buildDeliveryTitle(),
                body = buildDeliveryBody(analysis),
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

    private fun buildDeliveryBody(analysis: IncidentAnalysis): String = if (analysis.included) {
        "원인: ${analysis.causeEstimation}\n해결: ${analysis.remediation}"
    } else {
        "원인분석 실패(${analysis.errorType}) — 원지표만 확인하세요."
    }

    companion object {
        /** 신규 알림 생성(RAISED) — 쿨다운을 획득했을 때만 호출된다. */
        fun create(signal: AlertSignal, env: String): Alert = Alert(
            signalKey = signal.cooldownKey(env),
            endpoint = signal.endpoint,
            source = signal.source,
            severity = signal.severity,
            env = env,
            status = AlertStatus.RAISED,
            analysis = null,
            analysisIncluded = null,
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
            analysis: IncidentAnalysis?,
            analysisIncluded: Boolean?,
            raisedAt: ZonedDateTime,
            deliveredAt: ZonedDateTime?,
        ): Alert = Alert(
            signalKey = signalKey,
            endpoint = endpoint,
            source = source,
            severity = severity,
            env = env,
            status = status,
            analysis = analysis,
            analysisIncluded = analysisIncluded,
            raisedAt = raisedAt,
            deliveredAt = deliveredAt,
        )
    }
}
