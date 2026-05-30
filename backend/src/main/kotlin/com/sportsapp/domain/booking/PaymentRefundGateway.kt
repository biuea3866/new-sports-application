package com.sportsapp.domain.booking

import java.math.BigDecimal

data class RefundResult(
    val externalRefundId: String,
    val refundedAmount: BigDecimal,
    val message: String,
)

interface PaymentRefundGateway {
    fun requestRefund(paymentId: String, amount: BigDecimal, reason: String): RefundResult
}
