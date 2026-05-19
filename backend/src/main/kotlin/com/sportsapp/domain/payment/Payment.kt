package com.sportsapp.domain.payment

import java.math.BigDecimal
import java.time.ZonedDateTime

class Payment private constructor(
    val id: Long,
    val userId: Long,
    val idempotencyKey: String,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
    var status: PaymentStatus,
    val createdAt: ZonedDateTime,
    var paidAt: ZonedDateTime?,
    var failureReason: String?,
) {
    fun markCompleted(paidAt: ZonedDateTime) {
        if (!status.canTransitTo(PaymentStatus.COMPLETED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.COMPLETED)
        }
        this.status = PaymentStatus.COMPLETED
        this.paidAt = paidAt
    }

    fun markFailed(reason: String) {
        if (reason.isBlank()) throw InvalidFailureReasonException()
        if (!status.canTransitTo(PaymentStatus.FAILED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.FAILED)
        }
        this.status = PaymentStatus.FAILED
        this.failureReason = reason
    }

    fun markRefunded() {
        if (!status.canTransitTo(PaymentStatus.REFUNDED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.REFUNDED)
        }
        this.status = PaymentStatus.REFUNDED
    }

    companion object {
        fun create(
            userId: Long,
            idempotencyKey: String,
            orderType: OrderType,
            orderId: Long,
            method: PaymentMethod,
            amount: BigDecimal,
            currency: String,
        ): Payment = Payment(
            id = 0L,
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
            status = PaymentStatus.PENDING,
            createdAt = ZonedDateTime.now(),
            paidAt = null,
            failureReason = null,
        )

        fun reconstruct(snapshot: PaymentSnapshot): Payment = Payment(
            id = snapshot.id,
            userId = snapshot.userId,
            idempotencyKey = snapshot.idempotencyKey,
            orderType = snapshot.orderType,
            orderId = snapshot.orderId,
            method = snapshot.method,
            amount = snapshot.amount,
            currency = snapshot.currency,
            status = snapshot.status,
            createdAt = snapshot.createdAt,
            paidAt = snapshot.paidAt,
            failureReason = snapshot.failureReason,
        )
    }
}
