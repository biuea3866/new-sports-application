package com.sportsapp.application.booking

import com.sportsapp.domain.payment.PaymentMethod
import java.math.BigDecimal

data class CreateBookingCommand(
    val userId: Long,
    val slotId: Long,
    val paymentMethod: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
)
