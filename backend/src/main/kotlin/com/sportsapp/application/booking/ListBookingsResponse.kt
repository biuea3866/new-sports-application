package com.sportsapp.application.booking

import org.springframework.data.domain.Page

data class ListBookingsResponse(
    val content: List<BookingResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<BookingResponse>): ListBookingsResponse = ListBookingsResponse(
            content = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
