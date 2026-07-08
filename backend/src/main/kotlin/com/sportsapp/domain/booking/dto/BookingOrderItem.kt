package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * order 통합 조회가 소비하는 사용자별 Booking 읽기 프로젝션.
 *
 * title은 booking 컨텍스트 자기 데이터(Slot.date·Slot.timeRange)로 구성한 서술형 라벨이다.
 * 시설/프로그램의 사람이 읽는 이름은 facility 컨텍스트에 있어 조회 시 참조하면 R1(도메인 교차)
 * 위반이므로, Slot 부재·삭제 시에는 [DEFAULT_TITLE]로 방어 반환한다.
 */
data class BookingOrderItem(
    val bookingId: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val title: String,
    val createdAt: ZonedDateTime,
) {
    companion object {
        private const val DEFAULT_TITLE = "시설 예약"
        private val SLOT_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        fun of(
            bookingId: Long,
            slotId: Long,
            userId: Long,
            status: BookingStatus,
            paymentId: Long?,
            createdAt: ZonedDateTime,
            slotDate: ZonedDateTime?,
            slotTimeRange: String?,
        ): BookingOrderItem = BookingOrderItem(
            bookingId = bookingId,
            slotId = slotId,
            userId = userId,
            status = status,
            paymentId = paymentId,
            title = labelFor(slotDate, slotTimeRange),
            createdAt = createdAt,
        )

        private fun labelFor(slotDate: ZonedDateTime?, slotTimeRange: String?): String {
            if (slotDate == null || slotTimeRange == null) return DEFAULT_TITLE
            return "${slotDate.format(SLOT_DATE_FORMATTER)} $slotTimeRange $DEFAULT_TITLE"
        }
    }
}
