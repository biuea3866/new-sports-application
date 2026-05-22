package com.sportsapp.application.booking

import java.math.BigDecimal

data class RefundBookingCommand(
    val bookingId: Long,
    val refundAmount: BigDecimal,
    val reason: String,
)
