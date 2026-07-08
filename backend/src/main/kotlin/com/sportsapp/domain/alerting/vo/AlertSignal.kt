package com.sportsapp.domain.alerting.vo

/**
 * 쿨다운·dedup 판단 단위 (TDD.md §Terminology "알람 신호").
 * `API 엔드포인트 + source + severity` 조합으로 신호를 식별한다 (FR-7).
 */
data class AlertSignal(
    val endpoint: String,
    val source: AlertSource,
    val severity: AlertSeverity,
) {
    /**
     * INFRA-01 Redis 쿨다운 키 계약과 정확히 일치하는 키를 생성한다.
     * 형식: `alerting:cooldown:{env}:{endpoint}:{source}:{severity}` (source·severity는 소문자).
     */
    fun cooldownKey(env: String): String =
        "alerting:cooldown:$env:$endpoint:${source.name.lowercase()}:${severity.name.lowercase()}"
}
