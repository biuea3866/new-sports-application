package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus
import java.time.ZonedDateTime

/**
 * TicketOrder 단건 상세 조회 결과 — 표시명(이벤트 제목)·원본 이벤트 id 조합 프로젝션.
 * 참조 Event가 없거나 삭제된 경우 [eventTitle]은 빈 문자열로 방어된다
 * (list 프로젝션 [TicketOrderWithEventTitle]과 동일 정책).
 */
data class TicketOrderDetail(
    val ticketOrderId: Long,
    val status: OrderStatus,
    val eventId: Long,
    val eventTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
)
