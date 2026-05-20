package com.sportsapp.domain.booking

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED;

    fun canTransitTo(target: BookingStatus): Boolean = when (this) {
        PENDING -> target == CONFIRMED || target == CANCELLED || target == EXPIRED
        CONFIRMED -> target == CANCELLED
        CANCELLED -> false
        EXPIRED -> false
    }
}
