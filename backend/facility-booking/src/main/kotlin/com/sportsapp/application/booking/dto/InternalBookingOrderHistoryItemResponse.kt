package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.dto.BookingOrderItem
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * edge 통합 주문내역(BE-08)이 `OrderHistoryGateway.findBookingOrders` 원격 구현(2단계)으로 소비할
 * 계약 응답 (S2-04). detailPath("/bookings/{sourceId}") 조립은 edge 파사드가 sourceId로
 * 수행하므로 여기 포함하지 않는다(S2-04 티켓 "변경 사항" 참고). [BookingOrderItem]의 slotId·userId는
 * 계약에 없는 필드라 노출하지 않는다.
 *
 * [amount]는 booking 자기 컬럼(V65)이다 — edge 가 payment 를 역참조하지 않고 주문내역에 금액을
 * 노출하기 위해 공급자가 채운다. 이 컬럼 도입 이전 예약은 저장 이력이 없어 `null`(금액 확정 불가)이고,
 * `0`(무료 확정값)과 구분해야 하므로 0으로 방어하지 않는다.
 */
data class InternalBookingOrderHistoryItemResponse(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
) {
    companion object {
        fun of(item: BookingOrderItem): InternalBookingOrderHistoryItemResponse = InternalBookingOrderHistoryItemResponse(
            sourceId = item.bookingId,
            title = item.title,
            status = item.status.name,
            paymentId = item.paymentId,
            createdAt = item.createdAt,
            amount = item.amount,
        )
    }
}
