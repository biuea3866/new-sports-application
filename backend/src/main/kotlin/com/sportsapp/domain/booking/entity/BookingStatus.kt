package com.sportsapp.domain.booking.entity

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    REFUNDED;

    fun canTransitTo(target: BookingStatus): Boolean = when (this) {
        PENDING -> target == CONFIRMED || target == CANCELLED || target == EXPIRED
        CONFIRMED -> target == CANCELLED || target == REFUNDED
        CANCELLED -> false
        EXPIRED -> false
        REFUNDED -> false
    }
}
