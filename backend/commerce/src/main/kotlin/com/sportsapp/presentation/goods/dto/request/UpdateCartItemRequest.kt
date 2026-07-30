package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.UpdateCartItemCommand

data class UpdateCartItemRequest(
    val quantity: Int,
) {
    fun toCommand(userId: Long, itemId: Long) = UpdateCartItemCommand(
        userId = userId,
        itemId = itemId,
        quantity = quantity,
    )
}
