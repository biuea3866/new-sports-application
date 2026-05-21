package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.exception.LockExpiredException
import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import com.sportsapp.domain.ticketing.exception.SeatAlreadyLockedException
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

private val SEAT_LOCK_TTL = Duration.ofSeconds(300)
private val logger = LoggerFactory.getLogger(TicketingDomainService::class.java)

@Service
class TicketingDomainService(
    private val eventRepository: EventRepository,
    private val seatRepository: SeatRepository,
    private val eventCustomRepository: EventCustomRepository,
    private val seatCustomRepository: SeatCustomRepository,
    private val ticketOrderCustomRepository: TicketOrderCustomRepository,
    private val seatLockStore: SeatLockStore,
    private val ticketOrderRepository: TicketOrderRepository,
    private val ticketRepository: TicketRepository,
) {
    fun createEvent(
        title: String,
        venue: String,
        startsAt: ZonedDateTime,
        seats: List<SeatSpec>,
        ownerUserId: Long,
    ): Event {
        Event.validateSeatLimit(seats)
        val event = eventRepository.save(Event.create(title, venue, startsAt, ownerUserId))
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
        eventCustomRepository.findByCriteria(criteria, pageable)

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

    fun verifyLockOwner(lockId: String, userId: Long) {
        parseLockId(lockId).forEach { (eventId, seatId) ->
            val owner = seatLockStore.getOwner(eventId, seatId)
                ?: throw LockExpiredException(eventId, seatId)
            if (owner != userId) throw SeatNotLockOwnerException(eventId, seatId)
        }
    }

    @Transactional
    fun createPendingOrder(lockId: String, userId: Long): TicketOrder {
        val pairs = parseLockId(lockId)
        val eventId = pairs.first().first
        val seatIds = pairs.map { it.second }
        val order = TicketOrder.create(
            userId = userId,
            lockedEventId = eventId,
            lockedSeatIds = seatIds,
        )
        return ticketOrderRepository.save(order)
    }

    @Transactional
    fun confirmOrder(orderId: Long, paymentId: Long): TicketOrder {
        val order = ticketOrderRepository.findById(orderId)
            ?: throw ResourceNotFoundException("TicketOrder", orderId)
        val tickets = order.confirm(paymentId, order.lockedSeatIds)
        val saved = ticketOrderRepository.save(order)
        if (tickets.isNotEmpty()) ticketRepository.saveAll(tickets)
        return saved
    }

    @Transactional
    fun cancelOrder(orderId: Long) {
        val order = ticketOrderRepository.findById(orderId)
            ?: throw ResourceNotFoundException("TicketOrder", orderId)
        order.cancel()
        ticketOrderRepository.save(order)
        order.lockedSeatIds.forEach { seatId ->
            runCatching { seatLockStore.unlock(order.lockedEventId, seatId, order.userId) }
                .onFailure { logger.warn("Failed to unlock seat $seatId for event ${order.lockedEventId}: ${it.message}") }
        }
    }

    fun calculateAmount(lockId: String): BigDecimal {
        val pairs = parseLockId(lockId)
        val eventId = pairs.first().first
        val seatIds = pairs.map { it.second }
        val seats = seatRepository.findByEventId(eventId)
        return seats.filter { it.id in seatIds }.sumOf { it.price }
    }

    fun countEventsByOwnerIdGroupByStatus(ownerId: Long): Map<EventStatus, Long> =
        eventRepository.countByOwnerIdGroupByStatus(ownerId)

    fun sumTotalSeatsByOwnerId(ownerId: Long): Long =
        seatCustomRepository.sumTotalSeatsByOwnerId(ownerId)

    fun sumSoldSeatsByOwnerId(ownerId: Long): Long =
        seatCustomRepository.sumSoldSeatsByOwnerId(ownerId)

    fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary =
        ticketOrderCustomRepository.aggregateTicketSales(ownerUserId, eventId, from, to)

    fun findEventsByOwnerId(ownerId: Long, pageable: Pageable, status: EventStatus?): Page<Event> =
        eventRepository.findByOwnerId(ownerId, status, pageable)

    fun getEventSalesInfo(eventId: Long): EventSalesInfo {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        val seats = seatRepository.findByEventId(eventId)
        val soldCount = seatCustomRepository.countSoldByEventId(eventId)
        return EventSalesInfo(event = event, seats = seats, soldCount = soldCount)
    }

    fun openEvent(eventId: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.openSales()
        eventRepository.save(event)
    }

    fun closeEvent(eventId: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.close()
        eventRepository.save(event)
    }

    private fun parseLockId(lockId: String): List<Pair<Long, Long>> =
        lockId.split(",").map { token ->
            val parts = token.split(":")
            if (parts.size != 2) throw MalformedLockIdException(lockId)
            val eventId = parts[0].toLongOrNull() ?: throw MalformedLockIdException(lockId)
            val seatId = parts[1].toLongOrNull() ?: throw MalformedLockIdException(lockId)
            eventId to seatId
        }
}

data class SeatSpec(
    val section: String,
    val rowNo: String,
    val seatNo: String,
    val price: BigDecimal,
)
