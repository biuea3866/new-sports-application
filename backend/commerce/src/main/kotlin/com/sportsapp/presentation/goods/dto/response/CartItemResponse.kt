package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.entity.CartItem

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
