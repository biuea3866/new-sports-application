package com.sportsapp.domain.goods.entity

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "cart_items")
class CartItem(
    @Column(name = "cart_id", nullable = false)
    val cartId: Long,

    @Column(name = "product_id", nullable = false)
    val productId: Long,

    @Column(name = "quantity", nullable = false)
    var quantity: Int,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    // UNIQUE(cart_id, product_id, active_marker) 보조 컬럼 — 활성 item 은 1, soft-delete 시 NULL.
    // 동시 INSERT 시 DB unique 가 같은 (cart_id, product_id) 활성 row 중복을 차단(최종 방어선)한다.
    @Column(name = "active_marker")
    var activeMarker: Long? = 1
        protected set

    // 동시 addQuantity 경합에서 lost update 방지 — 충돌 시 OptimisticLock → @Retryable 재시도로 정확 합산.
    @Version
    @Column(name = "version", nullable = false)
    val version: Long = 0L

    fun addQuantity(amount: Int) {
        require(amount > 0) { "추가 수량은 0보다 커야 합니다: $amount" }
        quantity += amount
    }

    fun updateQuantity(newQuantity: Int) {
        require(newQuantity > 0) { "수량은 0보다 커야 합니다: $newQuantity" }
        quantity = newQuantity
    }

    // soft-delete 시 active_marker 를 NULL 로 풀어 같은 상품 재추가가 unique 에 막히지 않게 한다.
    override fun softDelete(userId: Long?) {
        super.softDelete(userId)
        activeMarker = null
    }
}
