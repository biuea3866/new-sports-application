package com.sportsapp.application.booking

import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingResult
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
        val bookingResult = bookingDomainService.requestBooking(command.userId, command.slotId)
        val payment = paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = buildIdempotencyKey(bookingResult),
            orderType = OrderType.BOOKING,
            orderId = bookingResult.bookingId,
            method = command.paymentMethod,
            amount = command.amount,
            currency = command.currency,
        )
        return CreateBookingResult(
            bookingId = bookingResult.bookingId,
            slotId = bookingResult.slotId,
            userId = bookingResult.userId,
            status = bookingResult.status,
            paymentId = payment.id,
        )
    }

    private fun buildIdempotencyKey(bookingResult: BookingResult): String =
        "booking:${bookingResult.bookingId}:${UUID.randomUUID()}"
}
