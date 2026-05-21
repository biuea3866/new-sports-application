package com.sportsapp.domain.payment

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED;

    fun canTransitTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target == COMPLETED || target == FAILED
        COMPLETED -> target == REFUNDED
        FAILED -> false
        REFUNDED -> false
    }
}
