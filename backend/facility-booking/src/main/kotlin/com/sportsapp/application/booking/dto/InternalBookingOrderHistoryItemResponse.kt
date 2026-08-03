package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.dto.BookingOrderItem
import java.time.ZonedDateTime

/**
 * edge 통합 주문내역(BE-08)이 `OrderHistoryGateway.findBookingOrders` 원격 구현(2단계)으로 소비할
 * 계약 응답 (S2-04). detailPath("/bookings/{sourceId}") 조립은 edge 파사드가 sourceId로
 * 수행하므로 여기 포함하지 않는다(S2-04 티켓 "변경 사항" 참고). [BookingOrderItem]의 slotId·userId는
 * 계약에 없는 필드라 노출하지 않는다.
 */
data class InternalBookingOrderHistoryItemResponse(
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(item: BookingOrderItem): InternalBookingOrderHistoryItemResponse = InternalBookingOrderHistoryItemResponse(
            sourceId = item.bookingId,
            title = item.title,
            status = item.status.name,
            paymentId = item.paymentId,
            createdAt = item.createdAt,
        )
    }
}
