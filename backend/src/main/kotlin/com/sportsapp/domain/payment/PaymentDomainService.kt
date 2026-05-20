package com.sportsapp.domain.payment

import java.math.BigDecimal
import org.springframework.stereotype.Service

@Service
class PaymentDomainService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
) {
    fun create(
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
        return processPayment(payment, idempotencyKey, method, amount, currency, orderType, orderId)
    }

    private fun processPayment(
        payment: Payment,
        idempotencyKey: String,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
        orderType: OrderType,
        orderId: Long,
    ): Payment {
        val paymentRequest = PaymentRequest(
            idempotencyKey = idempotencyKey,
            method = method,
            amount = amount,
            currency = currency,
            orderType = orderType,
            orderId = orderId,
        )
        return runCatching { paymentGateway.requestPayment(paymentRequest) }
            .fold(
                onSuccess = { result ->
                    payment.markCompleted(result.approvedAt)
                    paymentRepository.save(payment)
                },
                onFailure = { error ->
                    payment.markFailed(error.message ?: "PG 오류")
                    paymentRepository.save(payment)
                },
            )
    }

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
        // TODO: PAYMENT-02에서 paymentGateway.requestPayment() 호출 추가 예정
        paymentGateway.let { }
        return paymentRepository.save(payment)
    }

    fun findStatuses(paymentIds: List<Long>): Map<Long, PaymentStatus> {
        if (paymentIds.isEmpty()) return emptyMap()
        return paymentRepository.findAllByIdIn(paymentIds)
            .associate { it.id to it.status }
    }
}
