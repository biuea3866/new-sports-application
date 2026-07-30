package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.AddCartItemCommand

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
