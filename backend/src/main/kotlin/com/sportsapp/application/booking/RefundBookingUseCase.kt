package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundBookingUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(command: RefundBookingCommand): BookingResponse {
        val booking = bookingDomainService.refundBooking(
            bookingId = command.bookingId,
            callerUserId = command.callerUserId,
            refundAmount = command.refundAmount,
            reason = command.reason,
        )
        return BookingResponse.of(booking)
    }
}
