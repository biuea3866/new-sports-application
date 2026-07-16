package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.CreateBookingResult
import com.sportsapp.application.booking.dto.ListBookingsCommand
import com.sportsapp.application.booking.usecase.CancelBookingUseCase
import com.sportsapp.application.booking.usecase.CreateBookingUseCase
import com.sportsapp.application.booking.usecase.GetBookingUseCase
import com.sportsapp.application.booking.usecase.ListMyBookingsUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.booking.dto.request.CancelBookingRequest
import com.sportsapp.presentation.booking.dto.request.CreateBookingRequest
import com.sportsapp.presentation.booking.dto.response.BookingResponse
import com.sportsapp.presentation.booking.dto.response.ListBookingsResponse
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
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
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateBookingRequest,
    ): ResponseEntity<CreateBookingResult> {
        val result = createBookingUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.accepted().body(result)
    }

    @GetMapping("/me")
    fun listMyBookings(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListBookingsResponse> {
        val command = ListBookingsCommand(
            userId = principal.id,
            status = status,
            pageable = PageRequest.of(page, size),
        )
        val result = listMyBookingsUseCase.execute(command)
        return ResponseEntity.ok(ListBookingsResponse.of(result))
    }

    @GetMapping("/{id}")
    fun getBooking(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<BookingResponse> {
        val result = getBookingUseCase.execute(requesterId = principal.id, bookingId = id)
        return ResponseEntity.ok(BookingResponse.of(result))
    }

    @PostMapping("/{id}/cancel")
    fun cancelBooking(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @RequestBody request: CancelBookingRequest,
    ): ResponseEntity<BookingResponse> {
        val booking = cancelBookingUseCase.execute(request.toCommand(bookingId = id, userId = principal.id))
        return ResponseEntity.ok(BookingResponse.of(booking))
    }
}
