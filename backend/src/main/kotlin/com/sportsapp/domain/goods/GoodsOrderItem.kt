package com.sportsapp.domain.goods

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "goods_order_items")
class GoodsOrderItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    val order: GoodsOrder,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    val quantity: Int,

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    val unitPrice: BigDecimal,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    val subtotal: BigDecimal get() = unitPrice.multiply(BigDecimal(quantity))
}
