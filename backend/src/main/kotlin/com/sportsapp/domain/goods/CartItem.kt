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

@Entity
@Table(name = "cart_items")
class CartItem(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    val cart: Cart,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun addQuantity(amount: Int) {
        require(amount > 0) { "추가 수량은 0보다 커야 합니다: $amount" }
        quantity += amount
    }

    fun updateQuantity(newQuantity: Int) {
        require(newQuantity > 0) { "수량은 0보다 커야 합니다: $newQuantity" }
        quantity = newQuantity
    }
}
