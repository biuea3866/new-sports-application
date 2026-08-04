package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.Seat
import java.math.BigDecimal
import java.time.ZonedDateTime

data class SectionAvailability(
    val section: String,
    val totalSeats: Int,
    val minPrice: BigDecimal,
    val maxPrice: BigDecimal,
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
        fun of(event: Event, seatsWithAvailability: List<Pair<Seat, Boolean>>): EventDetailResponse =
            EventDetailResponse(
                id = event.id,
                title = event.title,
                venue = event.venue,
                startsAt = event.startsAt,
                status = event.status.name,
                sections = sectionAvailabilitiesOf(seatsWithAvailability),
                seats = seatInfosOf(seatsWithAvailability),
            )

        /** 구역별 좌석 수·최저가·최고가 요약 — 구역명 오름차순으로 정렬해 화면 순서를 고정한다. */
        private fun sectionAvailabilitiesOf(
            seatsWithAvailability: List<Pair<Seat, Boolean>>,
        ): List<SectionAvailability> = seatsWithAvailability
            .map { (seat, _) -> seat }
            .groupBy { it.section }
            .map { (section, sectionSeats) ->
                SectionAvailability(
                    section = section,
                    totalSeats = sectionSeats.size,
                    minPrice = sectionSeats.minOf { it.price },
                    maxPrice = sectionSeats.maxOf { it.price },
                )
            }
            .sortedBy { it.section }

        private fun seatInfosOf(seatsWithAvailability: List<Pair<Seat, Boolean>>): List<SeatInfo> =
            seatsWithAvailability.map { (seat, available) ->
                SeatInfo(
                    id = seat.id,
                    section = seat.section,
                    rowNo = seat.rowNo,
                    seatNo = seat.seatNo,
                    price = seat.price,
                    available = available,
                )
            }
    }
}
