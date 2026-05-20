package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.UpdateCartItemCommand

data class UpdateCartItemRequest(
    val quantity: Int,
) {
    fun toCommand(userId: Long, itemId: Long) = UpdateCartItemCommand(
        userId = userId,
        itemId = itemId,
        quantity = quantity,
    )
}
