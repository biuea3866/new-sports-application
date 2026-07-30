package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.service.SeatSpec
import java.time.ZonedDateTime

data class CreateMyEventCommand(
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val seats: List<SeatSpec>,
    val ownerUserId: Long,
)
