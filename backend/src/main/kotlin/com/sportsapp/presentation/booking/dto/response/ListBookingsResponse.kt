package com.sportsapp.presentation.booking.dto.response

import com.sportsapp.application.booking.dto.ListBookingsResult

data class ListBookingsResponse(
    val bookings: List<BookingResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(result: ListBookingsResult): ListBookingsResponse = ListBookingsResponse(
            bookings = result.bookings.map { BookingResponse.of(it) },
            totalElements = result.totalElements,
            totalPages = result.totalPages,
            page = result.page,
            size = result.size,
        )
    }
}
