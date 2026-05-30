package com.sportsapp.domain.payment

enum class PaymentStatus {
    PENDING,
    READY,
    COMPLETED,
    CANCELLED,
    FAILED,
    REFUNDED;

    fun canTransitTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target == READY || target == FAILED
        READY -> target == COMPLETED || target == CANCELLED || target == FAILED
        COMPLETED -> target == REFUNDED
        CANCELLED -> false
        FAILED -> false
        REFUNDED -> false
    }
}
