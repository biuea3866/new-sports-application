package com.sportsapp.application.payment

import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class PaymentResponse(
    val id: Long,
    val idempotencyKey: String,
    val status: PaymentStatus,
    val amount: BigDecimal,
    val currency: String,
    val paidAt: ZonedDateTime?,
    val failureReason: String?,
) {
    companion object {
        fun of(payment: Payment): PaymentResponse = PaymentResponse(
            id = payment.id,
            idempotencyKey = payment.idempotencyKey,
            status = payment.status,
            amount = payment.amount,
            currency = payment.currency,
            paidAt = payment.paidAt,
            failureReason = payment.failureReason,
        )
    }
}
