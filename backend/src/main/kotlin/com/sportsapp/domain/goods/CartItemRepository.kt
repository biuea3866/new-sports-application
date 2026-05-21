package com.sportsapp.domain.goods

interface CartItemRepository {
    fun save(cartItem: CartItem): CartItem
    fun saveAll(cartItems: List<CartItem>): List<CartItem>
    fun findById(id: Long): CartItem?
    fun findByCartId(cartId: Long): List<CartItem>
    fun findByCartIdAndProductId(cartId: Long, productId: Long): CartItem?
    fun findAllByCartId(cartId: Long): List<CartItem>
}
