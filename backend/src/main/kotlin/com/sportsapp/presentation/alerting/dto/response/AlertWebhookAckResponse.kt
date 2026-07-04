package com.sportsapp.presentation.alerting.dto.response

/**
 * webhook·내부 raise 즉시 응답 (TDD.md §Detail Design HTTP 계약 — 둘 다 202 Accepted 즉시 반환,
 * 처리는 비동기). 결과를 담지 않는다.
 */
data class AlertWebhookAckResponse(
    val accepted: Boolean,
) {
    companion object {
        fun accepted(): AlertWebhookAckResponse = AlertWebhookAckResponse(accepted = true)
    }
}
