package com.sportsapp.domain.payment

import java.math.BigDecimal

data class PgInitiateCommand(
    val paymentId: Long,
    val method: PaymentMethod,
    val idempotencyKey: String,
    val userId: Long,
    val orderType: OrderType,
    val orderId: Long,
    val amount: BigDecimal,
    val currency: String,
    val itemName: String,
    val returnUrl: String,
    val failUrl: String,
)
