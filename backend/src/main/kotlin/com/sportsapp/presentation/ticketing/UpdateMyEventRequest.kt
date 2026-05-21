package com.sportsapp.presentation.ticketing

import com.sportsapp.application.ticketing.UpdateMyEventCommand
import java.time.ZonedDateTime

data class UpdateMyEventRequest(
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
) {
    fun toCommand(eventId: Long, ownerUserId: Long): UpdateMyEventCommand = UpdateMyEventCommand(
        eventId = eventId,
        ownerUserId = ownerUserId,
        title = title,
        venue = venue,
        startsAt = startsAt,
    )
}
