package com.sportsapp.domain.payment

import java.math.BigDecimal

interface OrderPaymentGateway {
    fun createPayment(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Payment
}
