package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 사용자별 TicketOrder 조회 결과 — 표시명(이벤트 제목) 조인 프로젝션.
 * 참조 Event가 없거나 삭제된 경우 [eventTitle]은 빈 문자열로 방어된다.
 *
 * [paymentId]·[createdAt]은 order 통합조회(BE-08)의 결제 연계 노출·`createdAt desc` 병합에
 * 쓰인다 — 둘 다 TicketOrder 자기 컬럼이라 추가 조인 없이 노출한다.
 *
 * [totalAmount]는 TicketOrder가 잠근 좌석(`lockedSeatIds`)의 [com.sportsapp.domain.ticketing.entity.Seat.price]
 * 합계다 — ticketing 컨텍스트 자기 데이터(Seat)로만 구성해 결제 도메인을 역참조하지 않는다.
 * 좌석은 예매 시점에 항상 잠기므로 금액은 항상 확정값이다(null이 아니다). 참조 Seat가
 * 존재하지 않는 방어적 예외 상황에서는 존재하는 좌석만 합산한다(0건이면 0원).
 *
 * [seatSummary]는 좌석을 사람이 읽는 한 줄 요약이다(`Seat.displayLabel` 재사용, 2석 이상이면
 * "{대표 좌석} 외 {N-1}석"). 통합 주문내역(order-amount 결함 후속)에서 같은 이벤트의 서로 다른
 * 좌석 주문을 구분하는 데 쓰인다 — sourceId(내부 식별자) 노출 없이 좌석 정보로 구분한다.
 */
data class TicketOrderWithEventTitle(
    val ticketOrderId: Long,
    val status: OrderStatus,
    val eventTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val seatSummary: String = "",
)
