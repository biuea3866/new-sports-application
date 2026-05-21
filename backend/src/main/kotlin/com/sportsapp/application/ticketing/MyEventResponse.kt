package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Event
import java.time.ZonedDateTime

data class MyEventResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val ownerId: Long,
    val confirmedSeatCount: Long,
) {
    companion object {
        fun of(event: Event, confirmedSeatCount: Long): MyEventResponse = MyEventResponse(
            id = event.id,
            title = event.title,
            venue = event.venue,
            startsAt = event.startsAt,
            status = event.status.name,
            ownerId = event.ownerId,
            confirmedSeatCount = confirmedSeatCount,
        )

        fun of(event: Event): MyEventResponse = of(event, 0L)
    }
}
