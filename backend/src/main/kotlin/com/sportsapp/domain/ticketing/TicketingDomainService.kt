package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.ZonedDateTime

@Service
class TicketingDomainService(
    private val eventRepository: EventRepository,
    private val seatRepository: SeatRepository,
    private val customEventRepository: CustomEventRepository,
) {
    fun createEvent(
        title: String,
        venue: String,
        startsAt: ZonedDateTime,
        seats: List<SeatSpec>,
    ): Event {
        val event = eventRepository.save(Event.create(title, venue, startsAt))
        val seatList = seats.map { spec ->
            Seat(
                id = 0L,
                eventId = event.id,
                section = spec.section,
                rowNo = spec.rowNo,
                seatNo = spec.seatNo,
                price = spec.price,
            )
        }
        seatRepository.saveAll(seatList)
        return event
    }

    fun getEvent(eventId: Long): Event =
        eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)

    fun getSeats(eventId: Long): List<Seat> = seatRepository.findByEventId(eventId)

    fun listEvents(criteria: EventCriteria, pageable: Pageable): Page<Event> =
        customEventRepository.findByCriteria(criteria, pageable)
}

data class SeatSpec(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
