package com.sportsapp.domain.goods

import com.sportsapp.domain.common.JpaAuditingBase
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table

@Entity
@Table(name = "carts")
class Cart(
    @Column(name = "user_id", nullable = false)
    val userId: Long,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @OneToMany(mappedBy = "cart", cascade = [CascadeType.PERSIST, CascadeType.MERGE], fetch = FetchType.LAZY)
    val items: MutableList<CartItem> = mutableListOf()

    fun addItem(item: CartItem) {
        items.add(item)
    }

    /**
     * UNIQUE(user_id, active_marker) 제약으로 활성 cart 단일성을 보장한다.
     * - 활성 상태: active_marker = 1 (상수 — user_id 당 활성 cart 1건만 허용)
     * - 비활성(soft-delete): active_marker = null (null 은 unique 체크 제외)
     *
     * markActive()는 @PrePersist에서 자동 호출된다.
     * softDelete() 호출 시 markInactive()가 함께 호출된다.
     */
    @Column(name = "active_marker")
    var activeMarker: Long? = null
        private set

    @PrePersist
    private fun onPrePersist() {
        if (!isDeleted) markActive()
    }

    @PreUpdate
    private fun onPreUpdate() {
        if (isDeleted) markInactive()
    }

    fun markActive() {
        activeMarker = ACTIVE_MARKER
    }

    fun markInactive() {
        activeMarker = null
    }

    companion object {
        const val ACTIVE_MARKER = 1L
    }
}
