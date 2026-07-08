package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.LimitedDropPurchaseResult
import com.sportsapp.domain.goods.entity.GoodsOrderStatus

/**
 * 한정판 구매 응답(`POST /limited-drops/{dropId}/orders`). 202 Accepted — 주문은 PENDING(선점) 상태.
 */
data class LimitedDropPurchaseResponse(
    val orderId: Long,
    val dropId: Long,
    val status: GoodsOrderStatus,
) {
    companion object {
        fun of(result: LimitedDropPurchaseResult): LimitedDropPurchaseResponse = LimitedDropPurchaseResponse(
            orderId = result.orderId,
            dropId = result.dropId,
            status = result.status,
        )
    }
}
