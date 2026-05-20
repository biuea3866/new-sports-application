package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderItem
import com.sportsapp.domain.goods.GoodsOrderStatus
import java.math.BigDecimal

data class GoodsOrderResponse(
    val id: Long,
    val userId: Long,
    val status: GoodsOrderStatus,
    val totalAmount: BigDecimal,
    val paymentId: Long?,
    val items: List<GoodsOrderItemResponse>,
) {
    companion object {
        fun of(order: GoodsOrder, items: List<GoodsOrderItem>) = GoodsOrderResponse(
            id = order.id,
            userId = order.userId,
            status = order.status,
            totalAmount = order.totalAmount,
            paymentId = order.paymentId,
            items = items.map { GoodsOrderItemResponse.of(it) },
        )
    }
}
