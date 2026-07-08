package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.OrderWithPayment
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.math.BigDecimal

data class GoodsOrderResponse(
    val id: Long,
    val userId: Long?,
    val status: GoodsOrderStatus?,
    val totalAmount: BigDecimal,
    val paymentId: Long?,
    val paymentStatus: PaymentStatus?,
    val items: List<GoodsOrderItemResponse>,
) {
    companion object {
        fun of(order: GoodsOrder, items: List<GoodsOrderItem>) = GoodsOrderResponse(
            id = order.id,
            userId = order.userId,
            status = order.status,
            totalAmount = order.totalAmount,
            paymentId = order.paymentId,
            paymentStatus = null,
            items = items.map { GoodsOrderItemResponse.of(it) },
        )

        fun ofCreated(result: OrderWithPayment) = GoodsOrderResponse(
            id = result.orderId,
            userId = null,
            status = null,
            totalAmount = result.totalAmount,
            paymentId = result.paymentId,
            paymentStatus = result.paymentStatus,
            items = emptyList(),
        )
    }
}
