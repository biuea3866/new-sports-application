package com.sportsapp.application.booking.dto

import java.math.BigDecimal

data class RefundBookingCommand(
    val bookingId: Long,
    val callerUserId: Long,
    val refundAmount: BigDecimal,
    val reason: String,
)
