package com.sportsapp.presentation.alerting.dto.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.sportsapp.application.alerting.dto.GrafanaWebhookCommand

/**
 * Grafana Alerting webhook contact point 표준 페이로드 (TDD.md §Detail Design HTTP 계약,
 * `POST /internal/alerts/grafana`). `alertname`·`annotations`·`startsAt` 등 매핑 대상이 아닌
 * 나머지 필드는 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class GrafanaWebhookRequest(
    val alerts: List<GrafanaAlertItem>,
) {
    fun toCommands(): List<GrafanaWebhookCommand> = alerts.map { it.toCommand() }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GrafanaAlertItem(
    val labels: GrafanaAlertLabels,
) {
    fun toCommand(): GrafanaWebhookCommand = labels.toCommand()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class GrafanaAlertLabels(
    val endpoint: String,
    val source: String,
    val severity: String,
    val env: String,
) {
    fun toCommand(): GrafanaWebhookCommand = GrafanaWebhookCommand(
        endpoint = endpoint,
        source = source,
        severity = severity,
        env = env,
    )
}
