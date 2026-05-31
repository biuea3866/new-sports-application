package com.sportsapp.domain.booking

data class BookingResult(
    val bookingId: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
)
