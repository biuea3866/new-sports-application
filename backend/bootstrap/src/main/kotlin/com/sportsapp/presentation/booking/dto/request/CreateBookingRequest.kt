package com.sportsapp.presentation.booking.dto.request

import com.sportsapp.application.booking.dto.CreateBookingCommand
import com.sportsapp.domain.payment.vo.PaymentMethod
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
