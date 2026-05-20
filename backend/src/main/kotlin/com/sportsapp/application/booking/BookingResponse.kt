package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import java.time.ZonedDateTime

data class BookingResponse(
    val id: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
    val paymentId: Long?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    companion object {
        fun of(booking: Booking): BookingResponse = BookingResponse(
            id = booking.id,
            slotId = booking.slotId,
            userId = booking.userId,
            status = booking.status,
            paymentId = booking.paymentId,
            createdAt = booking.createdAt,
            updatedAt = booking.updatedAt,
        )
    }
}
