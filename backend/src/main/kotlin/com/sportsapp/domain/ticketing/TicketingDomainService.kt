package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.exception.SeatAlreadyLockedException
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

private val SEAT_LOCK_TTL = Duration.ofSeconds(300)

@Service
class TicketingDomainService(
    private val eventRepository: EventRepository,
    private val seatRepository: SeatRepository,
    private val customEventRepository: CustomEventRepository,
    private val seatLockStore: SeatLockStore,
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

    fun tryLockSeats(eventId: Long, seatIds: List<Long>, userId: Long): String {
        val lockedSeatIds = mutableListOf<Long>()
        for (seatId in seatIds) {
            val acquired = seatLockStore.tryLock(eventId, seatId, userId, SEAT_LOCK_TTL)
            if (!acquired) {
                lockedSeatIds.forEach { releasedId ->
                    seatLockStore.unlock(eventId, releasedId, userId)
                }
                throw SeatAlreadyLockedException(eventId, seatId)
            }
            lockedSeatIds.add(seatId)
        }
        return seatIds.joinToString(",") { "$eventId:$it" }
    }

    fun releaseSeats(eventId: Long, seatIds: List<Long>, userId: Long) {
        for (seatId in seatIds) {
            val released = seatLockStore.unlock(eventId, seatId, userId)
            if (!released) throw SeatNotLockOwnerException(eventId, seatId)
        }
    }
}

data class SeatSpec(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
