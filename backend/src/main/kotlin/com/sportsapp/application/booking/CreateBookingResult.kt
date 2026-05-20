package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus

data class CreateBookingResult(
    val bookingId: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long,
) {
    companion object {
        fun of(booking: Booking, paymentId: Long): CreateBookingResult = CreateBookingResult(
            bookingId = booking.id,
            slotId = booking.slotId,
            userId = booking.userId,
            status = booking.status,
            paymentId = paymentId,
        )
    }
}
