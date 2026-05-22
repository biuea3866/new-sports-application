package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CancelBookingUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(command: CancelBookingCommand): BookingResponse {
        val booking = bookingDomainService.cancel(command.bookingId, command.cancelledByUserId, command.reason)
        return BookingResponse.of(booking)
    }
}
