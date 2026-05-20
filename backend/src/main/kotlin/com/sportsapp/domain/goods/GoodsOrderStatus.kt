package com.sportsapp.domain.goods

enum class GoodsOrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    SHIPPED,
    DELIVERED,
    ;

    fun canTransitTo(next: GoodsOrderStatus): Boolean = when (this) {
        PENDING -> next == CONFIRMED || next == CANCELLED
        CONFIRMED -> next == SHIPPED || next == CANCELLED
        SHIPPED -> next == DELIVERED
        CANCELLED, DELIVERED -> false
    }
}
