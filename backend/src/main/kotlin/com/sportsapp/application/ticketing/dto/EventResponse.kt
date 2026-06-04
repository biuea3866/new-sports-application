package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import java.time.ZonedDateTime

data class EventResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
) {
    companion object {
        fun of(event: Event): EventResponse = EventResponse(
            id = event.id,
            title = event.title,
            venue = event.venue,
            startsAt = event.startsAt,
            status = event.status.name,
        )
    }
}
