package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.CreateBookingResult
import com.sportsapp.application.booking.dto.ListBookingsCommand
import com.sportsapp.application.booking.usecase.CancelBookingUseCase
import com.sportsapp.application.booking.usecase.CreateBookingUseCase
import com.sportsapp.application.booking.usecase.GetBookingUseCase
import com.sportsapp.application.booking.usecase.ListMyBookingsUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.presentation.booking.dto.request.CancelBookingRequest
import com.sportsapp.presentation.booking.dto.request.CreateBookingRequest
import com.sportsapp.presentation.booking.dto.response.BookingResponse
import com.sportsapp.presentation.booking.dto.response.ListBookingsResponse
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/bookings")
class BookingApiController(
    private val listMyBookingsUseCase: ListMyBookingsUseCase,
    private val getBookingUseCase: GetBookingUseCase,
    private val createBookingUseCase: CreateBookingUseCase,
    private val cancelBookingUseCase: CancelBookingUseCase,
) {
    @PostMapping
    fun createBooking(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: CreateBookingRequest,
    ): ResponseEntity<CreateBookingResult> {
        val result = createBookingUseCase.execute(request.toCommand(userId))
        return ResponseEntity.accepted().body(result)
    }

    @GetMapping("/me")
    fun listMyBookings(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListBookingsResponse> {
        val command = ListBookingsCommand(
            userId = userId,
            status = status,
            pageable = PageRequest.of(page, size),
        )
        val result = listMyBookingsUseCase.execute(command)
        return ResponseEntity.ok(ListBookingsResponse.of(result))
    }

    @GetMapping("/{id}")
    fun getBooking(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<BookingResponse> {
        val result = getBookingUseCase.execute(requesterId = userId, bookingId = id)
        return ResponseEntity.ok(BookingResponse.of(result))
    }

    @PostMapping("/{id}/cancel")
    fun cancelBooking(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
        @RequestBody request: CancelBookingRequest,
    ): ResponseEntity<BookingResponse> {
        val booking = cancelBookingUseCase.execute(request.toCommand(bookingId = id, userId = userId))
        return ResponseEntity.ok(BookingResponse.of(booking))
    }
}
