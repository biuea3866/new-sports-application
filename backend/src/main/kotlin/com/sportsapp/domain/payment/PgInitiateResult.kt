package com.sportsapp.domain.payment

data class PgInitiateResult(
    val paymentId: Long,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val checkoutUrl: String?,
)
