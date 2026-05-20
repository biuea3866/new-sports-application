package com.sportsapp.application.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentMethod
import java.math.BigDecimal

data class CreatePaymentCommand(
    val userId: Long,
    val idempotencyKey: String,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
)
