package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.entity.LimitedDrop

/**
 * [com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase] 반환값.
 * 구매 성공 시 주문은 PENDING 상태로 생성된다.
 */
data class LimitedDropPurchaseResult(
    val orderId: Long,
    val dropId: Long,
    val status: GoodsOrderStatus,
) {
    companion object {
        fun of(order: GoodsOrder, drop: LimitedDrop): LimitedDropPurchaseResult = LimitedDropPurchaseResult(
            orderId = order.id,
            dropId = drop.id,
            status = order.status,
        )
    }
}
