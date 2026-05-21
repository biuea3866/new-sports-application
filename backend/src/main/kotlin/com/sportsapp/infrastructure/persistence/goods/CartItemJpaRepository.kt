package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.CartItem
import org.springframework.data.jpa.repository.JpaRepository

interface CartItemJpaRepository : JpaRepository<CartItem, Long> {
    fun findAllByCartIdAndDeletedAtIsNull(cartId: Long): List<CartItem>
    fun findByCartIdAndProductIdAndDeletedAtIsNull(cartId: Long, productId: Long): CartItem?
    fun findAllByCartId(cartId: Long): List<CartItem>
}
