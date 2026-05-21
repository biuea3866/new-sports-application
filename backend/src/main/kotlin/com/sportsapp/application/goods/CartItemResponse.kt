package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartItem

data class CartItemResponse(
    val id: Long,
    val productId: Long,
    val quantity: Int,
) {
    companion object {
        fun of(cartItem: CartItem) = CartItemResponse(
            id = cartItem.id,
            productId = cartItem.productId,
            quantity = cartItem.quantity,
        )
    }
}
