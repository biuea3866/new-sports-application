package com.sportsapp.domain.goods.entity

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
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.goods.exception.NotGoodsOrderOwnerException
import com.sportsapp.domain.goods.entity.GoodsOrderStatus

@Entity
@Table(name = "goods_orders")
class GoodsOrder(
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "idempotency_key", nullable = true, unique = true, length = 255)
    val idempotencyKey: String?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: GoodsOrderStatus,

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    val totalAmount: BigDecimal,

    @Column(name = "payment_id", nullable = true)
    var paymentId: Long?,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun requireOwnedBy(requesterId: Long) {
        if (userId != requesterId) throw NotGoodsOrderOwnerException(id)
    }

    fun markPaid(paymentId: Long) {
        if (!status.canTransitTo(GoodsOrderStatus.CONFIRMED)) {
            throw InvalidGoodsOrderStateException(status, GoodsOrderStatus.CONFIRMED)
        }
        this.status = GoodsOrderStatus.CONFIRMED
        this.paymentId = paymentId
    }

    fun cancel() {
        if (!status.canTransitTo(GoodsOrderStatus.CANCELLED)) {
            throw InvalidGoodsOrderStateException(status, GoodsOrderStatus.CANCELLED)
        }
        this.status = GoodsOrderStatus.CANCELLED
    }

    fun markShipped() {
        if (!status.canTransitTo(GoodsOrderStatus.SHIPPED)) {
            throw InvalidGoodsOrderStateException(status, GoodsOrderStatus.SHIPPED)
        }
        this.status = GoodsOrderStatus.SHIPPED
    }

    companion object {
        fun create(userId: Long, totalAmount: BigDecimal, idempotencyKey: String? = null): GoodsOrder =
            GoodsOrder(
                userId = userId,
                idempotencyKey = idempotencyKey,
                status = GoodsOrderStatus.PENDING,
                totalAmount = totalAmount,
                paymentId = null,
            )
    }
}
