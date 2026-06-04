package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.CartItem
import org.springframework.data.jpa.repository.JpaRepository

interface CartItemJpaRepository : JpaRepository<CartItem, Long> {
    fun findAllByCart_IdAndDeletedAtIsNull(cartId: Long): List<CartItem>
    fun findByCart_IdAndProductIdAndDeletedAtIsNull(cartId: Long, productId: Long): CartItem?
}
