package com.sportsapp.application.order.dto

import com.sportsapp.domain.common.order.OrderType

/**
 * 통합 주문내역(BE-08) 조회 조건 — presentation이 Request에서 변환해 전달한다.
 *
 * [status]는 4개 주문 도메인이 각자 다른 enum(GoodsOrderStatus·OrderStatus·ApplicationStatus·
 * BookingStatus)을 쓰므로 원본 status enum name 문자열로 받는다(TDD "OrderHistoryCriteria").
 */
data class OrderHistoryCriteria(
    val orderType: OrderType?,
    val status: String?,
    val page: Int,
    val size: Int,
)
