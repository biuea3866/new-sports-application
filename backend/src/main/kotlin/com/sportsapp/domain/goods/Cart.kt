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
}
