package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.CreateBookingCommand
import com.sportsapp.application.booking.dto.CreateBookingResult
import com.sportsapp.domain.booking.dto.BookingResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class CreateBookingUseCase(
    private val bookingDomainService: BookingDomainService,
    private val paymentDomainService: PaymentDomainService,
    private val transactionTemplate: TransactionTemplate,
) {
    fun execute(command: CreateBookingCommand): CreateBookingResult {
        val (bookingResult, paymentId, idempotencyKey) = persistBookingAndPendingPayment(command)
        val pgResult = paymentDomainService.initiatePg(
            PgInitiateCommand(
                paymentId = paymentId,
                method = command.paymentMethod,
                idempotencyKey = idempotencyKey,
                userId = command.userId,
                orderType = OrderType.BOOKING,
                orderId = bookingResult.bookingId,
                amount = command.amount,
                currency = command.currency,
                itemName = OrderType.BOOKING.displayName,
                returnUrl = "",
                failUrl = "",
            )
        )
        return CreateBookingResult(
            bookingId = bookingResult.bookingId,
            slotId = bookingResult.slotId,
            userId = bookingResult.userId,
            status = bookingResult.status,
            paymentId = pgResult.paymentId,
        )
    }

    private fun persistBookingAndPendingPayment(command: CreateBookingCommand): Triple<BookingResult, Long, String> {
        return transactionTemplate.execute {
            val bookingResult = bookingDomainService.requestBooking(command.userId, command.slotId)
            val idempotencyKey = buildIdempotencyKey(bookingResult)
            val paymentId = paymentDomainService.createPending(
                userId = command.userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = bookingResult.bookingId,
                method = command.paymentMethod,
                amount = command.amount,
                currency = command.currency,
            )
            Triple(bookingResult, paymentId, idempotencyKey)
        } ?: throw IllegalStateException("Transaction returned null")
    }

    private fun buildIdempotencyKey(bookingResult: BookingResult): String =
        "booking:${bookingResult.bookingId}:${UUID.randomUUID()}"
}
