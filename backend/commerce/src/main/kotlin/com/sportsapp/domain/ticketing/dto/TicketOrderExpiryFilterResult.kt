package com.sportsapp.domain.ticketing.dto

/**
 * [com.sportsapp.domain.ticketing.service.TicketingDomainService.filterExpirableTicketOrders] 판정 결과.
 *
 * booking(W1-11c)의 [com.sportsapp.domain.booking.dto.BookingExpiryFilterResult]와 동일하게
 * `skippedSettledCount`를 `expirableIds` 제외분과 분리해 반환한다 — settled(결제 완료)로
 * 건너뛴 건은 환불 판단이 필요한 이상 신호(웹훅 유실·컨슈머 다운으로 결제는 됐는데 주문이
 * 여전히 PENDING)인 반면, live(결제 진행 중)로 건너뛴 건은 정상 흐름이다. 두 사유를
 * 뭉뚱그려 하나의 skippedCount로만 계측하면 경보가 불가능하다.
 */
data class TicketOrderExpiryFilterResult(
    val expirableIds: List<Long>,
    val skippedSettledCount: Int,
)
