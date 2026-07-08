package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.domain.goods.entity.GoodsOrderItem
import java.math.BigDecimal

data class GoodsOrderItemResponse(
    val id: Long,
    val productId: Long,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal,
) {
    companion object {
        fun of(item: GoodsOrderItem) = GoodsOrderItemResponse(
            id = item.id,
            productId = item.productId,
            quantity = item.quantity,
            unitPrice = item.unitPrice,
            subtotal = item.subtotal,
        )
    }
}
