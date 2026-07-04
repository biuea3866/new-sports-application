package com.sportsapp.domain.alerting.dto

import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource

/**
 * [com.sportsapp.domain.alerting.service.AlertDomainService.raise] 실행 파라미터.
 * Grafana webhook(`POST /internal/alerts/grafana`)·내부 raise(`POST /internal/alerts`) 양쪽
 * HTTP 계약(TDD.md §Detail Design)을 동일하게 이 Command로 변환해 받는다.
 */
data class RaiseAlertCommand(
    val endpoint: String,
    val source: AlertSource,
    val severity: AlertSeverity,
    val env: String,
    val contextHint: String? = null,
)
