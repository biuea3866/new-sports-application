package com.sportsapp.application.order.dto

import com.sportsapp.domain.common.order.OrderType

/**
 * 통합 주문내역(BE-08) 응답 — [failedDomains]는 부분 실패(FR-11) 시 타임아웃/예외로
 * 제외된 도메인을 표기한다. 전체 요청은 실패시키지 않는다.
 */
data class OrderHistoryResponse(
    val items: List<OrderHistoryItem>,
    val page: Int,
    val size: Int,
    val failedDomains: List<OrderType>,
)
