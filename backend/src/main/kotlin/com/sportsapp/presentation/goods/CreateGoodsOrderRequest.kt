package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.CreateGoodsOrderCommand
import com.sportsapp.domain.goods.OrderItemInput

data class CreateGoodsOrderRequest(
    val items: List<OrderItemRequestEntry>,
) {
    fun toCommand(userId: Long): CreateGoodsOrderCommand =
        CreateGoodsOrderCommand(
            userId = userId,
            items = items.map { OrderItemInput(productId = it.productId, quantity = it.quantity) },
        )
}

data class OrderItemRequestEntry(
    val productId: Long,
    val quantity: Int,
)
