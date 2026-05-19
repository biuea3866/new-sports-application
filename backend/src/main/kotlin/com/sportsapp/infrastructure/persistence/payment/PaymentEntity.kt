package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentSnapshot
import com.sportsapp.domain.payment.PaymentStatus
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
class PaymentEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

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

    @Column(name = "created_at", nullable = false)
    val createdAt: ZonedDateTime,

    @Column(name = "paid_at", nullable = true)
    var paidAt: ZonedDateTime?,

    @Column(name = "failure_reason", nullable = true, length = 500)
    var failureReason: String?,
) {
    fun toDomain(): Payment = Payment.reconstruct(
        PaymentSnapshot(
            id = id,
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
            status = status,
            createdAt = createdAt,
            paidAt = paidAt,
            failureReason = failureReason,
        )
    )

    companion object {
        fun from(payment: Payment): PaymentEntity = PaymentEntity(
            id = payment.id,
            userId = payment.userId,
            idempotencyKey = payment.idempotencyKey,
            orderType = payment.orderType,
            orderId = payment.orderId,
            method = payment.method,
            amount = payment.amount,
            currency = payment.currency,
            status = payment.status,
            createdAt = payment.createdAt,
            paidAt = payment.paidAt,
            failureReason = payment.failureReason,
        )
    }
}
