package com.sportsapp.application.goods

import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderStatus
import java.math.BigDecimal

data class GoodsOrderSummaryResponse(
    val id: Long,
    val status: GoodsOrderStatus,
    val totalAmount: BigDecimal,
) {
    companion object {
        fun of(order: GoodsOrder) = GoodsOrderSummaryResponse(
            id = order.id,
            status = order.status,
            totalAmount = order.totalAmount,
        )
    }
}
