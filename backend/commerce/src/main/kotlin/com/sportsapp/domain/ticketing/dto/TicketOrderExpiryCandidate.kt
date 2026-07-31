package com.sportsapp.domain.ticketing.dto

import java.time.ZonedDateTime

/**
 * W1-11b 만료 스위퍼 후보 조회 결과 — 티켓 주문 id와 생성 시각(createdAt)을 함께 담는다.
 *
 * booking(W1-11c)의 [com.sportsapp.domain.booking.dto.BookingExpiryCandidate]와 동일한 구조를
 * 그대로 따른다 — createdAt은 느린 TTL(readyTtlMinutes) 앵커가 아니라
 * [com.sportsapp.domain.common.payment.OrderPaymentLiveness.Live.since](payment 발급 시각)가
 * 앵커다. 이 createdAt은 빠른 TTL(ttlMinutes) 갈래에서 `max(candidate.createdAt, attemptSince)`로
 * 재평가되는 용도로 쓰인다([TicketingDomainService.filterExpirableTicketOrders] 참고).
 */
data class TicketOrderExpiryCandidate(
    val orderId: Long,
    val createdAt: ZonedDateTime,
)
