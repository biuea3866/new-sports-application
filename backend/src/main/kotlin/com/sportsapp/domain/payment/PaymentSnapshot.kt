package com.sportsapp.domain.payment

import java.math.BigDecimal
import java.time.ZonedDateTime

data class PaymentSnapshot(
    val id: Long,
    val userId: Long,
    val idempotencyKey: String,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentStatus,
    val createdAt: ZonedDateTime,
    val paidAt: ZonedDateTime?,
    val failureReason: String?,
)
