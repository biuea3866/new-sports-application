package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.dto.BookingDetail
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

data class GetBookingResult(
    val id: Long,
    val slotId: Long,
    val facilityId: String?,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val paymentStatus: PaymentStatus?,
    val title: String?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        /**
         * Slot 조인 없는 경로(취소 응답 등)가 사용 — facilityId·title은 채우지 않는다.
         * 목록·단건 상세는 아래 BookingDetail 오버로드를 쓴다(라벨이 비면 화면이 예약 PK를
         * 대신 노출하게 된다).
         */
        fun of(booking: Booking, paymentStatus: PaymentStatus? = null): GetBookingResult = GetBookingResult(
            id = booking.id,
            slotId = booking.slotId,
            facilityId = null,
            userId = booking.userId,
            status = booking.status,
            paymentId = booking.paymentId,
            paymentStatus = paymentStatus,
            title = null,
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt,
        )

        /** Slot 조인이 포함된 단건 상세 조회(GetBookingUseCase)가 사용. */
        fun of(detail: BookingDetail, paymentStatus: PaymentStatus? = null): GetBookingResult = GetBookingResult(
            id = detail.bookingId,
            slotId = detail.slotId,
            facilityId = detail.facilityId,
            userId = detail.userId,
            status = detail.status,
            paymentId = detail.paymentId,
            paymentStatus = paymentStatus,
            title = detail.title,
            createdAt = detail.createdAt,
            updatedAt = detail.updatedAt,
        )
    }
}
