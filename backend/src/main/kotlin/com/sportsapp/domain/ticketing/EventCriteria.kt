package com.sportsapp.domain.ticketing

import java.time.ZonedDateTime

data class EventCriteria(
    val status: EventStatus?,
    val startsAtFrom: ZonedDateTime?,
    val startsAtTo: ZonedDateTime?,
)
