package com.sportsapp.application.alerting.dto

/**
 * Grafana Alerting webhook contact point가 보내는 alert 1건의 label 집합 (TDD.md §DTO 흐름
 * `GrafanaWebhookRequest → RaiseAlertCommand`). presentation이 `alerts[].labels`에서 1건씩 꺼내
 * 이 Command로 변환해 [com.sportsapp.application.alerting.usecase.ReceiveGrafanaAlertUseCase]를 호출한다.
 * source/severity는 Grafana 규칙 라벨의 원시 문자열이며, enum 파싱은 UseCase 경계에서 수행한다
 * (알 수 없는 값은 예외로 거부).
 */
data class GrafanaWebhookCommand(
    val endpoint: String,
    val source: String,
    val severity: String,
    val env: String,
    val contextHint: String? = null,
)
