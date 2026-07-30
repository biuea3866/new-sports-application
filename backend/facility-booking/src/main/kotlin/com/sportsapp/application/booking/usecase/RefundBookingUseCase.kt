package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.RefundBookingCommand
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RefundBookingUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(command: RefundBookingCommand): Booking =
        bookingDomainService.refundBooking(
            bookingId = command.bookingId,
            callerUserId = command.callerUserId,
            refundAmount = command.refundAmount,
            reason = command.reason,
        )
}
