package com.sportsapp.domain.payment.dto

import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
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
