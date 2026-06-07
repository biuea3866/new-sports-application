package com.sportsapp.application.booking.dto

data class CancelBookingCommand(
    val bookingId: Long,
    val cancelledByUserId: Long,
    val reason: String? = null,
)
