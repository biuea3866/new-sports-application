package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.CreateGoodsOrderCommand
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.payment.vo.PaymentMethod

data class CreateGoodsOrderRequest(
    val method: PaymentMethod,
    val fromCart: Boolean,
    val items: List<OrderItemRequestEntry>,
) {
    fun toCommand(userId: Long, idempotencyKey: String): CreateGoodsOrderCommand =
        CreateGoodsOrderCommand(
            userId = userId,
            idempotencyKey = idempotencyKey,
            method = method,
            fromCart = fromCart,
            items = items.map { OrderItemInput(productId = it.productId, quantity = it.quantity) },
        )
}

data class OrderItemRequestEntry(
    val productId: Long,
    val quantity: Int,
)
