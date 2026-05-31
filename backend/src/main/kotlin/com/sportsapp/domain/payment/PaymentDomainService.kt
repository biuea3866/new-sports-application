package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEventPublisher
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class PaymentDomainService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val orderConfirmationGateway: OrderConfirmationGateway,
    private val domainEventPublisher: DomainEventPublisher,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentDomainService::class.java)
    }

    fun prepare(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
        itemName: String,
        returnUrl: String,
        failUrl: String,
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

        val provider = method.toPgProviderName()
        val prepareResult = paymentGateway.prepare(
            PgPrepareRequest(
                provider = provider,
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
        )

        payment.markReady(
            tid = prepareResult.tid,
            provider = prepareResult.provider,
            checkoutUrl = prepareResult.checkoutUrl,
        )
        return paymentRepository.save(payment)
    }

    fun create(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Payment = prepare(
        userId = userId,
        idempotencyKey = idempotencyKey,
        orderType = orderType,
        orderId = orderId,
        method = method,
        amount = amount,
        currency = currency,
        itemName = "$orderType #$orderId",
        returnUrl = "",
        failUrl = "",
    )

    fun confirmWebhook(tid: String, eventType: String): Payment {
        val payment = paymentRepository.findByPgTransactionId(tid)
            ?: throw PaymentNotFoundException(-1L)

        return when (eventType) {
            "PAYMENT_APPROVED" -> {
                if (payment.status == PaymentStatus.COMPLETED) return payment
                payment.markCompleted(ZonedDateTime.now())
                val saved = paymentRepository.save(payment)
                orderConfirmationGateway.confirm(
                    orderType = saved.orderType,
                    orderId = saved.orderId,
                    paymentId = saved.id,
                )
                domainEventPublisher.publishAll(saved.pullDomainEvents())
                saved
            }
            "PAYMENT_CANCELED" -> {
                if (payment.status == PaymentStatus.CANCELLED) return payment
                payment.markCancelled()
                paymentRepository.save(payment)
            }
            else -> {
                log.warn("confirmWebhook: 미인식 eventType={} tid={}", eventType, tid)
                payment
            }
        }
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
