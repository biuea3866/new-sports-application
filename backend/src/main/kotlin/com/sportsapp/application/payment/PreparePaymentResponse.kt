package com.sportsapp.application.payment

import com.sportsapp.domain.payment.Payment

data class PreparePaymentResponse(
    val paymentId: Long,
    val checkoutUrl: String,
    val pgTransactionId: String,
) {
    companion object {
        fun of(payment: Payment): PreparePaymentResponse = PreparePaymentResponse(
            paymentId = payment.id,
            checkoutUrl = requireNotNull(payment.checkoutUrl) { "checkoutUrl must be set after prepare" },
            pgTransactionId = requireNotNull(payment.pgTransactionId) { "pgTransactionId must be set after prepare" },
        )
    }
}
