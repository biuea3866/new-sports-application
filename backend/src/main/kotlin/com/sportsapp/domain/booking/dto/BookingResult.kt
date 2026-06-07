package com.sportsapp.domain.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus

data class BookingResult(
    val bookingId: Long,
    val slotId: Long,
    val userId: Long,
    val status: BookingStatus,
)
