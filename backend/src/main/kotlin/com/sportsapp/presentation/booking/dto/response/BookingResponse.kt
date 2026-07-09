package com.sportsapp.presentation.booking.dto.response

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

data class BookingResponse(
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
        fun of(result: GetBookingResult): BookingResponse = BookingResponse(
            id = result.id,
            slotId = result.slotId,
            facilityId = result.facilityId,
            userId = result.userId,
            status = result.status,
            paymentId = result.paymentId,
            paymentStatus = result.paymentStatus,
            title = result.title,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
        )

        /** Slot 조인 없는 경로(cancelBooking 응답)가 사용 — facilityId·title은 채우지 않는다. */
        fun of(booking: Booking, paymentStatus: PaymentStatus? = null): BookingResponse = BookingResponse(
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
    }
}
