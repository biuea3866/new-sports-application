package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.entity.BookingStatus
import org.springframework.data.domain.Pageable

data class ListBookingsCommand(
    val userId: Long,
    val status: BookingStatus?,
    val pageable: Pageable,
)
