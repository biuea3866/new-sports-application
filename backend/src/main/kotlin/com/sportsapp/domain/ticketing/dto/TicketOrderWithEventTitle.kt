package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus

/**
 * 사용자별 TicketOrder 조회 결과 — 표시명(이벤트 제목) 조인 프로젝션.
 * 참조 Event가 없거나 삭제된 경우 [eventTitle]은 빈 문자열로 방어된다.
 */
data class TicketOrderWithEventTitle(
    val ticketOrderId: Long,
    val status: OrderStatus,
    val eventTitle: String,
)
