package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEventPublisher
import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class PaymentDomainService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val domainEventPublisher: DomainEventPublisher,
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
                    paymentRepository.save(payment).also {
                        domainEventPublisher.publishAll(payment.pullDomainEvents())
                    }
                },
                onFailure = { error ->
                    payment.markFailed(error.message ?: "PG 오류")
                    paymentRepository.save(payment).also {
                        domainEventPublisher.publishAll(payment.pullDomainEvents())
                    }
                },
            )
    }

    fun findStatuses(paymentIds: List<Long>): Map<Long, PaymentStatus> {
        if (paymentIds.isEmpty()) return emptyMap()
        return paymentRepository.findAllByIdIn(paymentIds)
            .associate { it.id to it.status }
    }

    fun getPayment(userId: Long, paymentId: Long): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)
        if (payment.userId != userId) throw NotPaymentOwnerException(paymentId)
        return payment
    }

    fun findMyPayments(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> = paymentRepository.findByUserIdAndConditions(
        userId = userId,
        status = status,
        paidAtFrom = paidAtFrom,
        paidAtTo = paidAtTo,
        pageable = pageable,
    )
}
