package com.sportsapp.domain.goods

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "stocks")
class Stock(
    @Id
    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(nullable = false)
    var quantity: Int,

    @Version
    @Column(nullable = false)
    val version: Long = 0L,
) : JpaAuditingBase() {
    fun deduct(amount: Int) {
        if (quantity < amount) throw OutOfStockException(productId, amount, quantity)
        quantity -= amount
    }

    fun restore(amount: Int) {
        if (amount <= 0) throw InvalidQuantityException(amount)
        quantity += amount
    }
}
