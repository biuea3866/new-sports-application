package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.vo.CartLineItem
import java.math.BigDecimal

data class CartResponse(
    val cartId: Long,
    val userId: Long,
    val items: List<CartItemResponse>,
    val totalAmount: BigDecimal,
) {
    companion object {
        fun of(cart: Cart, lineItems: List<CartLineItem>) = CartResponse(
            cartId = cart.id,
            userId = cart.userId,
            items = lineItems.map { CartItemResponse.of(it) },
            totalAmount = lineItems.fold(BigDecimal.ZERO) { sum, item -> sum + item.subtotal },
        )
    }
}
