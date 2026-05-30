package com.sportsapp.domain.payment

import java.math.BigDecimal

data class PaymentRequest(
    val idempotencyKey: String,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
    val orderType: OrderType,
    val orderId: Long,
)

data class PaymentGatewayResult(
    val pgTransactionId: String,
    val provider: String,
    val approvedAt: java.time.ZonedDateTime,
)

interface PaymentGateway {
    fun requestPayment(request: PaymentRequest): PaymentGatewayResult
}
