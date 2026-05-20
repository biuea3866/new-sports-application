package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.AddCartItemCommand

data class AddCartItemRequest(
    val productId: Long,
    val quantity: Int,
) {
    fun toCommand(userId: Long) = AddCartItemCommand(
        userId = userId,
        productId = productId,
        quantity = quantity,
    )
}
