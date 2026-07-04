package com.sportsapp.domain.alerting.vo

/**
 * 알림 발생 축(source) — 신호 단위 쿨다운/이력 태깅의 축 하나 (TDD.md §Terminology).
 */
enum class AlertSource {
    LATENCY,
    OVERSELL,
    DEPLOYMENT,
    SELF_CHECK,
}
