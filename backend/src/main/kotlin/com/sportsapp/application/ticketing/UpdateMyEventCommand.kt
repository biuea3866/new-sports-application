package com.sportsapp.application.ticketing

import java.time.ZonedDateTime

data class UpdateMyEventCommand(
    val eventId: Long,
    val ownerUserId: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
)
