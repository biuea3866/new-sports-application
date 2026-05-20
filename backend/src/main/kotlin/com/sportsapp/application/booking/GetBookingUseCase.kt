package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetBookingUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(requesterId: Long, bookingId: Long): BookingResponse {
        val booking = bookingDomainService.getBooking(requesterId, bookingId)
        val paymentStatus = booking.paymentId?.let {
            paymentDomainService.findStatuses(listOf(it))[it]
        }
        return BookingResponse.of(booking, paymentStatus)
    }
}
