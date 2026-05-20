package com.sportsapp.application.ticketing

import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.Seat
import java.time.ZonedDateTime

data class SectionAvailability(
    val section: String,
    val totalSeats: Int,
)

data class EventDetailResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val sections: List<SectionAvailability>,
) {
    companion object {
        fun of(event: Event, seats: List<Seat>): EventDetailResponse {
            val sectionAvailabilities = seats
                .groupBy { it.section }
                .map { (section, sectionSeats) -> SectionAvailability(section, sectionSeats.size) }
                .sortedBy { it.section }

            return EventDetailResponse(
                id = event.id,
                title = event.title,
                venue = event.venue,
                startsAt = event.startsAt,
                status = event.status.name,
                sections = sectionAvailabilities,
            )
        }
    }
}
