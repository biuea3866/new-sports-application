package com.sportsapp.domain.alerting.entity

/**
 * [Alert] 상태 전이 표 (TDD.md §상태 전이 표).
 * DELIVERED·DELIVERY_FAILED는 종료 상태로 어떤 전이도 허용하지 않는다.
 */
enum class AlertStatus {
    RAISED,
    ENRICHED,
    DELIVERED,
    DELIVERY_FAILED,
    ;

    fun canTransitTo(next: AlertStatus): Boolean = when (this) {
        RAISED -> next == ENRICHED
        ENRICHED -> next == DELIVERED || next == DELIVERY_FAILED
        DELIVERED -> false
        DELIVERY_FAILED -> false
    }
}
