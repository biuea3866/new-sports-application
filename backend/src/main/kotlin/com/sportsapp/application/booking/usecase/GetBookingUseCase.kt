package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.GetBookingResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetBookingUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(requesterId: Long, bookingId: Long): GetBookingResult {
        val booking = bookingDomainService.getBooking(requesterId, bookingId)
        val paymentStatus = booking.paymentId?.let {
            paymentDomainService.findStatuses(listOf(it))[it]
        }
        return GetBookingResult.of(booking, paymentStatus)
    }
}
