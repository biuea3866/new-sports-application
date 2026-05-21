package com.sportsapp.domain.booking

enum class BookingStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    EXPIRED,
    NO_SHOW;

    fun canTransitTo(target: BookingStatus): Boolean = when (this) {
        PENDING -> target == CONFIRMED || target == CANCELLED || target == EXPIRED
        CONFIRMED -> target == CANCELLED || target == NO_SHOW
        CANCELLED -> false
        EXPIRED -> false
        NO_SHOW -> false
    }
}
