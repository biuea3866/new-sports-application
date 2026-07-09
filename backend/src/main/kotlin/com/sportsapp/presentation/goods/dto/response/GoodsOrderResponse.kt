package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.OrderWithPayment
import com.sportsapp.domain.goods.dto.GoodsOrderDetail
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

data class GoodsOrderResponse(
    val id: Long,
    val userId: Long?,
    val status: GoodsOrderStatus?,
    val totalAmount: BigDecimal,
    val paymentId: Long?,
    val paymentStatus: PaymentStatus?,
    val title: String?,
    val createdAt: ZonedDateTime?,
    val items: List<GoodsOrderItemResponse>,
) {
    companion object {
        /**
         * 주문 상세(`GET /goods-orders/{orderId}`) 응답 — 통합 주문내역 리스트만큼 리치하도록
         * [title](대표 상품명)과 [createdAt]을 함께 채운다(Option A+). 기존 필드는 그대로 유지한다.
         */
        fun of(detail: GoodsOrderDetail) = GoodsOrderResponse(
            id = detail.order.id,
            userId = detail.order.userId,
            status = detail.order.status,
            totalAmount = detail.order.totalAmount,
            paymentId = detail.order.paymentId,
            paymentStatus = null,
            title = detail.title,
            createdAt = detail.order.createdAt,
            items = detail.items.map { GoodsOrderItemResponse.of(it) },
        )

        fun ofCreated(result: OrderWithPayment) = GoodsOrderResponse(
            id = result.orderId,
            userId = null,
            status = null,
            totalAmount = result.totalAmount,
            paymentId = result.paymentId,
            paymentStatus = result.paymentStatus,
            title = null,
            createdAt = null,
            items = emptyList(),
        )
    }
}
