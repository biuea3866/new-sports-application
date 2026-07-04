package com.sportsapp.presentation.alerting.dto.request

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource

/**
 * 내부 raise(③오버셀·⑧배포실패) 요청 본문 (TDD.md §Detail Design HTTP 계약, `POST /internal/alerts`).
 * source/severity 문자열은 이 경계에서 enum으로 파싱한다 — 알 수 없는 값은 [IllegalArgumentException](400)을 던진다.
 */
data class RaiseAlertRequest(
    val endpoint: String,
    val source: String,
    val severity: String,
    val env: String,
    val contextHint: String? = null,
) {
    fun toCommand(): RaiseAlertCommand = RaiseAlertCommand(
        endpoint = endpoint,
        source = AlertSource.valueOf(source.uppercase()),
        severity = AlertSeverity.valueOf(severity.uppercase()),
        env = env,
        contextHint = contextHint,
    )
}
