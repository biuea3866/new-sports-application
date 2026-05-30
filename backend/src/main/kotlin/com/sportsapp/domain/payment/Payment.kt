package com.sportsapp.domain.payment

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.ZonedDateTime

@Entity
@Table(name = "payments")
class Payment private constructor(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    val orderType: OrderType,

    @Column(name = "order_id", nullable = false)
    val orderId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    val method: PaymentMethod,

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    val amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: PaymentStatus,

    @Column(name = "paid_at", nullable = true)
    var paidAt: ZonedDateTime?,

    @Column(name = "failure_reason", nullable = true, length = 500)
    var failureReason: String?,

    @Column(name = "pg_transaction_id", nullable = true, length = 100)
    var pgTransactionId: String?,

    @Column(name = "provider", nullable = true, length = 32)
    var provider: String?,
) : JpaAuditingBase() {

    fun markCompleted(paidAt: ZonedDateTime, pgTransactionId: String, provider: String) {
        if (!status.canTransitTo(PaymentStatus.COMPLETED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.COMPLETED)
        }
        this.status = PaymentStatus.COMPLETED
        this.paidAt = paidAt
        this.pgTransactionId = pgTransactionId
        this.provider = provider
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
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
            status = PaymentStatus.PENDING,
            paidAt = null,
            failureReason = null,
            pgTransactionId = null,
            provider = null,
        )
    }
}
