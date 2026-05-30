package com.sportsapp.presentation.booking

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.application.booking.CreateBookingResult
import com.sportsapp.application.booking.CreateBookingUseCase
import com.sportsapp.application.booking.GetBookingUseCase
import com.sportsapp.application.booking.ListBookingsCommand
import com.sportsapp.application.booking.ListBookingsResponse
import com.sportsapp.application.booking.ListMyBookingsUseCase
import com.sportsapp.domain.booking.BookingStatus
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
        return ResponseEntity.ok(listMyBookingsUseCase.execute(command))
    }

    @GetMapping("/{id}")
    fun getBooking(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<BookingResponse> {
        return ResponseEntity.ok(getBookingUseCase.execute(requesterId = userId, bookingId = id))
    }

    @PostMapping("/{id}/cancel")
    fun cancelBooking(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
        @RequestBody request: CancelBookingRequest,
    ): ResponseEntity<BookingResponse> {
        return ResponseEntity.ok(cancelBookingUseCase.execute(request.toCommand(bookingId = id, userId = userId)))
    }
}
