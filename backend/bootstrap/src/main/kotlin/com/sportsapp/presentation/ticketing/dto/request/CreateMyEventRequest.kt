package com.sportsapp.presentation.ticketing.dto.request

import com.sportsapp.application.ticketing.dto.CreateMyEventCommand
import com.sportsapp.domain.ticketing.service.SeatSpec
import java.math.BigDecimal
import java.time.ZonedDateTime

data class SeatRequest(
    val sectionName: String,
    val seatLabel: String,
    val price: BigDecimal,
)

data class CreateMyEventRequest(
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val seats: List<SeatRequest>,
) {
    fun toCommand(ownerUserId: Long): CreateMyEventCommand = CreateMyEventCommand(
        title = title,
        venue = venue,
        startsAt = startsAt,
        seats = seats.map { seat ->
            SeatSpec(
                section = seat.sectionName,
                rowNo = "1",
                seatNo = seat.seatLabel,
                price = seat.price,
            )
        },
        ownerUserId = ownerUserId,
    )
}
