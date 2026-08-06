package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 통합 주문내역(BE-08)이 `OrderHistoryGateway.findGoodsOrders` 원격 구현(2단계)으로 소비할 계약
 * 응답 (S2-03).
 *
 * [amount] 는 `GoodsOrder.totalAmount` 다 — edge 가 payment 를 역참조하지 않고 주문내역에 금액을
 * 노출하기 위해 공급자가 채운다. [paymentId] 는 결제 전 주문에서 null 이고, 파사드의 "미결제" 판정
 * 입력이다.
 *
 * `orderType`(GOODS 고정)·`detailPath`(`/goods-orders/{sourceId}`)는 상수 판정이라 edge 파사드가
 * 만든다 — 이 응답에 두지 않는다. [title] 은 goods 컨텍스트가 자기 데이터로 구성한 대표 상품명이다
 * (기술 식별자를 쓰지 않는다).
 */
data class InternalGoodsOrderHistoryItemResponse(
    val sourceId: Long,
    val title: String,
    val status: GoodsOrderStatus,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal,
) {
    companion object {
        fun of(goodsOrderWithTitle: GoodsOrderWithTitle): InternalGoodsOrderHistoryItemResponse =
            InternalGoodsOrderHistoryItemResponse(
                sourceId = goodsOrderWithTitle.order.id,
                title = goodsOrderWithTitle.title,
                status = goodsOrderWithTitle.order.status,
                paymentId = goodsOrderWithTitle.order.paymentId,
                createdAt = goodsOrderWithTitle.order.createdAt,
                amount = goodsOrderWithTitle.order.totalAmount,
            )
    }
}
