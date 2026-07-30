package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.Seat
import java.math.BigDecimal
import java.time.ZonedDateTime

data class SectionAvailability(
    val section: String,
    val totalSeats: Int,
)

data class SeatInfo(
    val id: Long,
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
    val available: Boolean,
)

data class EventDetailResponse(
    val id: Long,
    val title: String,
    val venue: String,
    val startsAt: ZonedDateTime,
    val status: String,
    val sections: List<SectionAvailability>,
    val seats: List<SeatInfo>,
) {
    companion object {
        fun of(event: Event, seatsWithAvailability: List<Pair<Seat, Boolean>>): EventDetailResponse {
            val sectionAvailabilities = seatsWithAvailability
                .map { (seat, _) -> seat }
                .groupBy { it.section }
                .map { (section, sectionSeats) -> SectionAvailability(section, sectionSeats.size) }
                .sortedBy { it.section }

            val seatInfos = seatsWithAvailability.map { (seat, available) ->
                SeatInfo(
                    id = seat.id,
                    section = seat.section,
                    rowNo = seat.rowNo,
                    seatNo = seat.seatNo,
                    price = seat.price,
                    available = available,
                )
            }

            return EventDetailResponse(
                id = event.id,
                title = event.title,
                venue = event.venue,
                startsAt = event.startsAt,
                status = event.status.name,
                sections = sectionAvailabilities,
                seats = seatInfos,
            )
        }
    }
}
