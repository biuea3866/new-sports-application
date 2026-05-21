package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingStatus
import org.springframework.data.domain.Pageable

data class ListBookingsCommand(
    val userId: Long,
    val status: BookingStatus?,
    val pageable: Pageable,
)
