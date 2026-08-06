package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSeatLabel
import com.sportsapp.domain.ticketing.entity.OrderStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 통합 주문내역(BE-08)이 `OrderHistoryGateway.findTicketingOrders` 원격 구현(2단계)으로 소비할
 * 계약 응답 (S2-03).
 *
 * [seats] 는 **원본 필드로 실어 보낸다** — 문자열로 미리 조합하지 않는다. 모바일이 이미 티켓 구매
 * 확인 화면에서 쓰는 `formatSeatDescription`("A석구역 1열 05번")과 서식이 어긋나는 두 번째 포맷터가
 * 생기지 않게 하는 것이 목적이다. 같은 경기의 여러 좌석 주문을 사용자가 구분하는 근거이며, 내부
 * 식별자(sourceId) 대신 사람이 읽는 좌석 정보를 쓴다.
 *
 * 좌석이 없으면 **빈 목록**으로 보낸다 — "구분 정보 없음"의 null 판정은 edge 파사드가 한다
 * (`seats.takeIf { it.isNotEmpty() }`). `orderType`·`detailPath` 도 파사드가 만든다.
 */
data class InternalTicketingOrderHistoryItemResponse(
    val sourceId: Long,
    val title: String,
    val status: OrderStatus,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal,
    val seats: List<SeatResponse>,
) {
    /** 좌석 원본 필드 — 조합은 소비자(모바일 포맷터)가 한다. */
    data class SeatResponse(
        val section: String,
        val rowNo: String,
        val seatNo: String,
    ) {
        companion object {
            fun of(seatLabel: TicketSeatLabel): SeatResponse = SeatResponse(
                section = seatLabel.section,
                rowNo = seatLabel.rowNo,
                seatNo = seatLabel.seatNo,
            )
        }
    }

    companion object {
        fun of(ticketOrder: TicketOrderWithEventTitle): InternalTicketingOrderHistoryItemResponse =
            InternalTicketingOrderHistoryItemResponse(
                sourceId = ticketOrder.ticketOrderId,
                title = ticketOrder.eventTitle,
                status = ticketOrder.status,
                paymentId = ticketOrder.paymentId,
                createdAt = ticketOrder.createdAt,
                amount = ticketOrder.totalAmount,
                seats = ticketOrder.seats.map(SeatResponse::of),
            )
    }
}
