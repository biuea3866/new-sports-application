package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.vo.PaymentMethod

data class PurchaseTicketsCommand(
    val userId: Long,
    val lockId: String,
    val idempotencyKey: String,
    val method: PaymentMethod,
    val currency: String,
) {
    init {
        require(lockId.isNotBlank()) { "lockId must not be blank" }
        require(idempotencyKey.isNotBlank()) { "idempotencyKey must not be blank" }
    }
}
