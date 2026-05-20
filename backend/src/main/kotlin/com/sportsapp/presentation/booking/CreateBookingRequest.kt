package com.sportsapp.presentation.booking

import com.sportsapp.application.booking.CreateBookingCommand
import com.sportsapp.domain.payment.PaymentMethod
import java.math.BigDecimal

data class CreateBookingRequest(
    val slotId: Long,
    val paymentMethod: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
) {
    fun toCommand(userId: Long): CreateBookingCommand = CreateBookingCommand(
        userId = userId,
        slotId = slotId,
        paymentMethod = paymentMethod,
        amount = amount,
        currency = currency,
    )
}
