package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListBookingsCommand): ListBookingsResponse {
        val bookingPage = bookingDomainService.findMyBookings(
            userId = command.userId,
            status = command.status,
            pageable = command.pageable,
        )
        return ListBookingsResponse.of(bookingPage.map { BookingResponse.of(it) })
    }
}
