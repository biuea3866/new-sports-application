package com.sportsapp.domain.payment

import java.math.BigDecimal

class PaymentDomainService(
    private val paymentRepository: PaymentRepository,
    val paymentGateway: PaymentGateway,
) {
    fun initiatePayment(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Payment {
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing

        val payment = Payment.create(
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
        )
        return paymentRepository.save(payment)
    }
}
