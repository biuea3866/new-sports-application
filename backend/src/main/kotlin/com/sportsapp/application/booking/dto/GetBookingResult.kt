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
         * Slot 조인 없는 목록 조회(ListMyBookingsUseCase)가 사용 — facilityId·title은
         * 이 경로에서 아직 채우지 않는다(범위: booking 단건 상세 보강).
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
