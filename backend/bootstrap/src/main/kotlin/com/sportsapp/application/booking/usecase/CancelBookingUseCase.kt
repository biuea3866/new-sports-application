package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.CancelBookingCommand
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.service.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CancelBookingUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional
    fun execute(command: CancelBookingCommand): Booking =
        bookingDomainService.cancel(command.bookingId, command.cancelledByUserId, command.reason)
}
