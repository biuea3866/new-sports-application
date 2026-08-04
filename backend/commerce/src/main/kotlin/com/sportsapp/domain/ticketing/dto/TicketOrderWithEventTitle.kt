package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.OrderStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 잠긴 좌석 1건의 표시용 필드(section/rowNo/seatNo) — [com.sportsapp.domain.ticketing.entity.Seat]의
 * 표시 관련 컬럼만 담은 읽기 전용 프로젝션이다. 도메인/화면 조합용 서술형 문자열
 * (`Seat.displayLabel`)을 여기서 만들지 않는다 — 모바일이 이미 쓰는
 * `seat-format.ts#formatSeatDescription`("A석구역 1열 05번")과 서식이 어긋나는 두 번째
 * 포맷터가 생기는 것을 막기 위해, 조합은 모바일 쪽 단일 포맷터에 맡기고 여기서는 원본
 * 필드만 그대로 전달한다.
 */
data class TicketSeatLabel(
    val section: String,
    val rowNo: String,
    val seatNo: String,
)

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
 * [seats]는 잠긴 좌석 각각의 section/rowNo/seatNo다(`lockedSeatIds` 순서 보존). 통합
 * 주문내역에서 같은 이벤트의 서로 다른 좌석 주문을 sourceId(내부 식별자) 노출 없이
 * 구분하는 데 쓰인다 — 조합된 문자열이 아니라 원본 필드로 내려 모바일의 기존
 * `formatSeatDescription`(주문 확인 화면이 이미 쓰는 서식)으로 표시를 통일한다.
 */
data class TicketOrderWithEventTitle(
    val ticketOrderId: Long,
    val status: OrderStatus,
    val eventTitle: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val seats: List<TicketSeatLabel> = emptyList(),
)
