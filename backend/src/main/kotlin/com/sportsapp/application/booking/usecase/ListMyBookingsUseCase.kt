package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.application.booking.dto.ListBookingsCommand
import com.sportsapp.application.booking.dto.ListBookingsResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListBookingsCommand): ListBookingsResult {
        val bookingPage = bookingDomainService.findMyBookings(
            userId = command.userId,
            status = command.status,
            pageable = command.pageable,
        )
        val paymentIds = bookingPage.content.mapNotNull { it.paymentId }
        val paymentStatuses = paymentDomainService.findStatuses(paymentIds)
        return ListBookingsResult.of(
            bookingPage.map { booking ->
                GetBookingResult.of(booking, paymentStatuses[booking.paymentId])
            }
        )
    }
}
