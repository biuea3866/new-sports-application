package com.sportsapp.application.payment.dto

import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import java.math.BigDecimal

data class PreparePaymentCommand(
    val userId: Long,
    val idempotencyKey: String,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
    val itemName: String,
    val returnUrl: String,
    val failUrl: String,
)

fun PreparePaymentCommand.toInitiateCommand(paymentId: Long) = PgInitiateCommand(
    paymentId = paymentId,
    method = method,
    idempotencyKey = idempotencyKey,
    userId = userId,
    orderType = orderType,
    orderId = orderId,
    amount = amount,
    currency = currency,
    itemName = itemName,
    returnUrl = returnUrl,
    failUrl = failUrl,
)
