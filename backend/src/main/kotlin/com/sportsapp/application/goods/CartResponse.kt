package com.sportsapp.application.goods

import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartItem

data class CartResponse(
    val cartId: Long,
    val userId: Long,
    val items: List<CartItemResponse>,
) {
    companion object {
        fun of(cart: Cart, items: List<CartItem>) = CartResponse(
            cartId = cart.id,
            userId = cart.userId,
            items = items.map { CartItemResponse.of(it) },
        )
    }
}
