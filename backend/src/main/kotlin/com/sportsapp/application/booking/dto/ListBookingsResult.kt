package com.sportsapp.application.booking.dto

import org.springframework.data.domain.Page

data class ListBookingsResult(
    val bookings: List<GetBookingResult>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<GetBookingResult>): ListBookingsResult = ListBookingsResult(
            bookings = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
