package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import java.time.ZonedDateTime

/**
 * 단건 조회(GET /bookings/{id})가 소비하는 booking + slot 조인 상세 프로젝션.
 *
 * facilityId·title은 booking 컨텍스트 자기 데이터(Slot)로 구성한다. Slot이 삭제·부재인
 * 경우 facilityId는 원본 시설을 알 수 없으므로 null, title은 [BookingTitleLabel]의 기본
 * 라벨로 방어 반환한다 ([BookingOrderItem]과 동일한 방어 전략).
 */
data class BookingDetail(
    val bookingId: Long,
    val slotId: Long,
    val facilityId: String?,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val title: String,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun of(booking: Booking, slot: Slot?): BookingDetail = BookingDetail(
            bookingId = booking.id,
            slotId = booking.slotId,
            facilityId = slot?.facilityId,
            userId = booking.userId,
            status = booking.status,
            paymentId = booking.paymentId,
            title = BookingTitleLabel.of(slotDate = slot?.date, slotTimeRange = slot?.timeRange),
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt,
        )
    }
}
