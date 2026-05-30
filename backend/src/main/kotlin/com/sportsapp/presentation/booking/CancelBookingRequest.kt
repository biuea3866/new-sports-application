package com.sportsapp.presentation.booking

import com.sportsapp.application.booking.CancelBookingCommand

data class CancelBookingRequest(
    val reason: String? = null,
) {
    fun toCommand(bookingId: Long, userId: Long): CancelBookingCommand = CancelBookingCommand(
        bookingId = bookingId,
        cancelledByUserId = userId,
        reason = reason,
    )
}
