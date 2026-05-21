package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.domain.Page
import java.time.ZonedDateTime

data class NoShowBookingResponse(
    val bookingId: Long,
    val userId: Long,
    val slotId: Long,
    val status: BookingStatus,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(booking: Booking): NoShowBookingResponse = NoShowBookingResponse(
            bookingId = booking.id,
            userId = booking.userId,
            slotId = booking.slotId,
            status = booking.status,
            createdAt = booking.createdAt,
        )
    }
}

data class ListNoShowBookingsResponse(
    val bookings: List<NoShowBookingResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<NoShowBookingResponse>): ListNoShowBookingsResponse = ListNoShowBookingsResponse(
            bookings = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
