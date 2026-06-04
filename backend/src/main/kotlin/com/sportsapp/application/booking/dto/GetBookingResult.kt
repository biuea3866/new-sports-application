package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.payment.entity.PaymentStatus
import java.time.ZonedDateTime

data class GetBookingResult(
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
        fun of(booking: Booking, paymentStatus: PaymentStatus? = null): GetBookingResult = GetBookingResult(
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
