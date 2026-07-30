package com.sportsapp.domain.alerting.vo

/**
 * 심각도 등급 — source와 독립 태깅한다 (TDD.md §Terminology, FR-3).
 * [discordColor]는 Discord Embed 색상 필드(10진 RGB)로 사용된다.
 */
enum class AlertSeverity {
    INFO,
    WARN,
    CRITICAL,
    ;

    fun discordColor(): Int = when (this) {
        INFO -> 0x3498DB
        WARN -> 0xF1C40F
        CRITICAL -> 0xE74C3C
    }
}
