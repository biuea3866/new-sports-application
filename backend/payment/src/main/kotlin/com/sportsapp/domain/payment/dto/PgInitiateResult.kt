package com.sportsapp.domain.payment.dto

import com.sportsapp.domain.payment.entity.PaymentStatus

data class PgInitiateResult(
    val paymentId: Long,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val checkoutUrl: String?,
)
