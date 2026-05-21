package com.sportsapp.domain.payment

enum class PaymentStatus {
    PENDING,
    COMPLETED,
    PAID,
    FAILED,
    REFUNDED;

    fun canTransitTo(target: PaymentStatus): Boolean = when (this) {
        PENDING -> target == COMPLETED || target == PAID || target == FAILED
        COMPLETED -> target == REFUNDED
        PAID -> target == REFUNDED
        FAILED -> false
        REFUNDED -> false
    }
}
