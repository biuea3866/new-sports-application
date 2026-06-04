package com.sportsapp.presentation.booking.dto.response

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

data class BookingResponse(
    val id: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val paymentStatus: PaymentStatus?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun of(result: GetBookingResult): BookingResponse = BookingResponse(
            id = result.id,
            slotId = result.slotId,
            userId = result.userId,
            status = result.status,
            paymentId = result.paymentId,
            paymentStatus = result.paymentStatus,
            createdAt = result.createdAt,
            updatedAt = result.updatedAt,
        )

        fun of(booking: Booking, paymentStatus: PaymentStatus? = null): BookingResponse = BookingResponse(
            id = booking.id,
            slotId = booking.slotId,
            userId = booking.userId,
            status = booking.status,
            paymentId = booking.paymentId,
            paymentStatus = paymentStatus,
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt,
        )
    }
}
