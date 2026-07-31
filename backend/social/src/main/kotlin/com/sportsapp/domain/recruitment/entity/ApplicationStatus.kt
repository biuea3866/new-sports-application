package com.sportsapp.domain.recruitment.entity

enum class ApplicationStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REFUNDED;

    fun canTransitTo(target: ApplicationStatus): Boolean = when (this) {
        PENDING -> target == CONFIRMED || target == CANCELLED
        CONFIRMED -> target == CANCELLED
        CANCELLED -> target == REFUNDED
        REFUNDED -> false
    }
}
