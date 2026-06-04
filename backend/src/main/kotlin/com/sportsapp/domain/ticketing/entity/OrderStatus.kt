package com.sportsapp.domain.ticketing.entity

import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException

enum class OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    fun canTransitTo(target: OrderStatus): Boolean = when (this) {
        PENDING -> target == CONFIRMED || target == CANCELLED
        CONFIRMED -> false
        CANCELLED -> false
    }

    fun requireCanTransitTo(target: OrderStatus) {
        if (!canTransitTo(target)) {
            throw InvalidOrderStateException("Cannot transit from $this to $target")
        }
    }
}
