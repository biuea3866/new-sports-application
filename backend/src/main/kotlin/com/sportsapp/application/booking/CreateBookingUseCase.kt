package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class CreateBookingUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: CreateBookingCommand): CreateBookingResult {
        val booking = bookingDomainService.requestBooking(command.userId, command.slotId)
        val payment = paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = buildIdempotencyKey(booking),
            orderType = OrderType.BOOKING,
            orderId = booking.id,
            method = command.paymentMethod,
            amount = command.amount,
            currency = command.currency,
        )
        return CreateBookingResult.of(booking, payment.id)
    }

    private fun buildIdempotencyKey(booking: Booking): String =
        "booking:${booking.id}:${UUID.randomUUID()}"
}
