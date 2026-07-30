package com.sportsapp.application.alerting.usecase

import com.sportsapp.application.alerting.dto.GrafanaWebhookCommand
import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.service.AlertDomainService
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Grafana Alerting webhook 수신 Command → [RaiseAlertCommand] 변환 → raise 위임
 * (TDD.md §Detail Design 시스템 역할 경계, `POST /internal/alerts/grafana`).
 */
@Service
class ReceiveGrafanaAlertUseCase(
    private val alertDomainService: AlertDomainService,
) {
    @Transactional
    fun execute(command: GrafanaWebhookCommand): Alert? =
        alertDomainService.raise(command.toRaiseAlertCommand())

    private fun GrafanaWebhookCommand.toRaiseAlertCommand(): RaiseAlertCommand = RaiseAlertCommand(
        endpoint = endpoint,
        source = AlertSource.valueOf(source.uppercase()),
        severity = AlertSeverity.valueOf(severity.uppercase()),
        env = env,
        contextHint = contextHint,
    )
}
