package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
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

    @Column(name = "pg_transaction_id", nullable = true, length = 100)
    var pgTransactionId: String?,

    @Column(name = "provider", nullable = true, length = 32)
    var provider: String?,

    @Column(name = "checkout_url", nullable = true, length = 500)
    var checkoutUrl: String?,

    @Column(name = "paid_at", nullable = true)
    var paidAt: ZonedDateTime?,

    @Column(name = "failure_reason", nullable = true, length = 500)
    var failureReason: String?,

    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0,
) : JpaAuditingBase() {

    @Transient
    private var _domainEvents: MutableList<DomainEvent>? = null

    private val domainEvents: MutableList<DomainEvent>
        get() = _domainEvents ?: mutableListOf<DomainEvent>().also { _domainEvents = it }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun registerEvent(event: DomainEvent) {
        domainEvents.add(event)
    }

    fun markReady(tid: String, provider: String, checkoutUrl: String) {
        if (!status.canTransitTo(PaymentStatus.READY)) {
            throw InvalidPaymentStateException(status, PaymentStatus.READY)
        }
        this.status = PaymentStatus.READY
        this.pgTransactionId = tid
        this.provider = provider
        this.checkoutUrl = checkoutUrl
    }

    fun markCompleted(paidAt: ZonedDateTime) {
        if (!status.canTransitTo(PaymentStatus.COMPLETED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.COMPLETED)
        }
        this.status = PaymentStatus.COMPLETED
        this.paidAt = paidAt
        registerEvent(PaymentCompletedEvent(paymentId = id))
    }

    fun markCancelled() {
        if (!status.canTransitTo(PaymentStatus.CANCELLED)) {
            throw InvalidPaymentStateException(status, PaymentStatus.CANCELLED)
        }
        this.status = PaymentStatus.CANCELLED
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
            pgTransactionId = null,
            provider = null,
            checkoutUrl = null,
            paidAt = null,
            failureReason = null,
            version = 0,
        )
    }
}
