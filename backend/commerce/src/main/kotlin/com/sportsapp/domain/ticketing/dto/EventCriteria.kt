package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.EventStatus
import java.time.ZonedDateTime

data class EventCriteria(
    val status: EventStatus?,
    val startsAtFrom: ZonedDateTime?,
    val startsAtTo: ZonedDateTime?,
    val keyword: String? = null,
)
