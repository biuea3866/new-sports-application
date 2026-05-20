package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyBookingsUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListBookingsCommand): ListBookingsResponse {
        val bookingPage = bookingDomainService.findMyBookings(
            userId = command.userId,
            status = command.status,
            pageable = command.pageable,
        )
        val paymentIds = bookingPage.content.mapNotNull { it.paymentId }
        val paymentStatuses = paymentDomainService.findStatuses(paymentIds)
        return ListBookingsResponse.of(
            bookingPage.map { booking ->
                BookingResponse.of(booking, paymentStatuses[booking.paymentId])
            }
        )
    }
}
