package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
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
