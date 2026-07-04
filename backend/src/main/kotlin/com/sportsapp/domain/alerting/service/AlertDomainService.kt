package com.sportsapp.domain.alerting.service

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.event.AlertDeliveryReadyEvent
import com.sportsapp.domain.alerting.exception.AlertNotFoundException
import com.sportsapp.domain.alerting.exception.IncidentAnalysisException
import com.sportsapp.domain.alerting.gateway.IncidentAnalysisGateway
import com.sportsapp.domain.alerting.gateway.TelemetryQueryGateway
import com.sportsapp.domain.alerting.repository.AlertCooldownRepository
import com.sportsapp.domain.alerting.repository.AlertRepository
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.IncidentAnalysis
import com.sportsapp.domain.alerting.vo.IncidentContext
import com.sportsapp.domain.common.DomainEventPublisher
import java.time.Duration
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * 알림 라이프사이클(신호·쿨다운·LLM분석·발송트리거) 오케스트레이션 (TDD.md §Detail Design).
 *
 * [env]는 self-check heartbeat(FR 없음, PRD Operations)에 태깅할 배포 환경 값이다. `raise`/`process`는
 * Alert 자신의 [Alert.env](webhook·내부 raise가 전달한 값)를 사용하므로 이 값과 무관하다.
 *
 * BE-07: `env`는 `@Value("\${app.env:local}")`로 주입한다. domain 레이어 원칙상 Spring 설정
 * 어노테이션은 지양 대상이나, 이 서비스가 이미 `@Service`로 Spring 빈 조립 대상이고(선행 BE-01 결정),
 * 이 코드베이스에 `@Configuration` 기반 별도 조립 계층이 없어 가장 단순한 해소 방법이다
 * (BE-10 `application.yml`의 `app.env: \${APP_ENV:local}`을 그대로 소비, 테스트는 named argument로
 * 직접 문자열을 넘기므로 영향받지 않는다).
 */
@Service
class AlertDomainService(
    private val alertRepository: AlertRepository,
    private val alertCooldownRepository: AlertCooldownRepository,
    private val telemetryQueryGateway: TelemetryQueryGateway,
    private val incidentAnalysisGateway: IncidentAnalysisGateway,
    private val domainEventPublisher: DomainEventPublisher,
    @Value("\${app.env:local}") private val env: String,
) {

    /** 쿨다운 미획득 시 null(억제). 획득 시 Alert(RAISED)를 저장하고 처리 이벤트를 발행한다. */
    fun raise(command: RaiseAlertCommand): Alert? {
        val signal = AlertSignal(command.endpoint, command.source, command.severity)
        if (!alertCooldownRepository.tryAcquire(signal, COOLDOWN_DURATION)) return null
        val saved = alertRepository.save(Alert.create(signal, command.env))
        saved.requestProcessing()
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    /** 텔레메트리 조회 → LLM 분석(실패 시 폴백) → attachAnalysis → 발송 이벤트 발행. */
    fun process(alertId: Long) {
        val alert = findById(alertId)
        val signal = AlertSignal(alert.endpoint, alert.source, alert.severity)
        val analysis = analyze(signal, alert.env)
        alert.attachAnalysis(analysis)
        val saved = alertRepository.save(alert)
        domainEventPublisher.publishAll(saved.pullDomainEvents())
    }

    /** 쿨다운·LLM분석 미적용 — SELF_CHECK/INFO heartbeat 발송 이벤트를 즉시 발행한다. */
    fun selfCheck() {
        domainEventPublisher.publish(
            AlertDeliveryReadyEvent(
                alertId = SELF_CHECK_ALERT_ID,
                title = SELF_CHECK_TITLE,
                body = SELF_CHECK_BODY,
                source = AlertSource.SELF_CHECK,
                severity = AlertSeverity.INFO,
                env = env,
            )
        )
    }

    private fun analyze(signal: AlertSignal, env: String): IncidentAnalysis {
        val snapshot = telemetryQueryGateway.queryContext(signal, LOOKBACK_DURATION)
        return try {
            incidentAnalysisGateway.analyze(IncidentContext(signal, env, snapshot))
        } catch (exception: IncidentAnalysisException) {
            IncidentAnalysis.fallback()
        }
    }

    private fun findById(alertId: Long): Alert =
        alertRepository.findById(alertId) ?: throw AlertNotFoundException(alertId)

    companion object {
        private val COOLDOWN_DURATION: Duration = Duration.ofMinutes(15)
        private val LOOKBACK_DURATION: Duration = Duration.ofMinutes(10)
        private const val SELF_CHECK_ALERT_ID = 0L
        private const val SELF_CHECK_TITLE = "지능형 장애 알림 self-check"
        private const val SELF_CHECK_BODY = "알림 파이프라인이 정상 동작 중입니다."
    }
}
