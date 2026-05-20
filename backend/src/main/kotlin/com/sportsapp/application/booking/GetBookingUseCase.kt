package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetBookingUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(requesterId: Long, bookingId: Long): BookingResponse {
        val booking = bookingDomainService.getBooking(requesterId, bookingId)
        return BookingResponse.of(booking)
    }
}
