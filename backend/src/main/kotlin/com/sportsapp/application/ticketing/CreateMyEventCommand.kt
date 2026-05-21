package com.sportsapp.application.ticketing

import java.math.BigDecimal
import java.time.ZonedDateTime

data class CreateMyEventCommand(
    val ownerUserId: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val seats: List<SeatSpecCommand>,
)

data class SeatSpecCommand(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
