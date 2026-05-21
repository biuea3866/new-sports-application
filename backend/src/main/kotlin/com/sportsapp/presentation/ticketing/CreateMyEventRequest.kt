package com.sportsapp.presentation.ticketing

import com.sportsapp.application.ticketing.CreateMyEventCommand
import com.sportsapp.application.ticketing.SeatSpecCommand
import java.math.BigDecimal
import java.time.ZonedDateTime

data class CreateMyEventRequest(
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val seats: List<SeatSpecRequest>,
) {
    fun toCommand(ownerUserId: Long): CreateMyEventCommand = CreateMyEventCommand(
        ownerUserId = ownerUserId,
        title = title,
        venue = venue,
        startsAt = startsAt,
        seats = seats.map { SeatSpecCommand(it.section, it.rowNo, it.seatNo, it.price) },
    )
}

data class SeatSpecRequest(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
