package com.sportsapp.domain.payment

import java.math.BigDecimal

data class PgPrepareRequest(
    val provider: String,
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

data class PgPrepareResult(
    val tid: String,
    val provider: String,
    val checkoutUrl: String,
)

interface PaymentGateway {
    fun prepare(request: PgPrepareRequest): PgPrepareResult
}
