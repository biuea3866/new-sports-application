package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.SeatSpec
import java.time.ZonedDateTime

data class CreateMyEventCommand(
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val seats: List<SeatSpec>,
    val ownerUserId: Long,
)
