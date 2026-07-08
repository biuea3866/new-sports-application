package com.sportsapp.domain.ticketing.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.dto.EventSalesInfo
import com.sportsapp.domain.ticketing.dto.TicketKpiSummary
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSalesSummary
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.event.TicketEvent
import com.sportsapp.domain.ticketing.exception.LockExpiredException
import com.sportsapp.domain.ticketing.exception.MalformedLockIdException
import com.sportsapp.domain.ticketing.exception.SeatAlreadyLockedException
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.dto.EventCriteria
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.SeatCustomRepository
import com.sportsapp.domain.ticketing.repository.SeatRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.math.BigDecimal
import java.math.RoundingMode
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
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun createEvent(
        title: String,
        venue: String,
        startsAt: ZonedDateTime,
        seats: List<SeatSpec>,
        ownerUserId: Long,
    ): Event {
        Event.validateSeatLimit(seats)
        Event.validateNoDuplicateSeats(seats) { Triple(it.section, it.rowNo, it.seatNo) }
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

    fun getSeatsWithAvailability(eventId: Long): List<Pair<Seat, Boolean>> {
        val seats = seatRepository.findByEventId(eventId)
        return seats.map { seat -> seat to (seatLockStore.getOwner(eventId, seat.id) == null) }
    }

    fun listEvents(criteria: EventCriteria, pageable: Pageable): Page<Event> =
        eventCustomRepository.findByCriteria(criteria, pageable)

    // catalog 통합검색용 — status=OPEN 고정 + keyword 부분 일치. CLOSED/CANCELLED는 결과에서 제외한다.
    fun searchOpenEvents(keyword: String?, pageable: Pageable): Page<Event> =
        listEvents(
            EventCriteria(status = EventStatus.OPEN, startsAtFrom = null, startsAtTo = null, keyword = keyword),
            pageable,
        )

    // order 통합조회용 — TicketOrder에 이벤트명(title)을 조인한 표시용 프로젝션.
    fun listTicketOrdersBy(userId: Long): List<TicketOrderWithEventTitle> =
        ticketOrderCustomRepository.findBy(userId)

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

    fun getTicketOrder(ticketOrderId: Long): TicketOrder =
        ticketOrderRepository.findById(ticketOrderId)
            ?: throw ResourceNotFoundException("TicketOrder", ticketOrderId)

    @Transactional
    fun createPendingOrder(lockId: String, userId: Long): TicketOrderResult {
        val pairs = parseLockId(lockId)
        val eventId = pairs.first().first
        val seatIds = pairs.map { it.second }
        val order = TicketOrder.create(
            userId = userId,
            lockedEventId = eventId,
            lockedSeatIds = seatIds,
        )
        val saved = ticketOrderRepository.save(order)
        return TicketOrderResult.of(saved)
    }

    fun confirmOrder(orderId: Long, paymentId: Long): TicketOrderResult {
        val order = ticketOrderRepository.findById(orderId)
            ?: throw ResourceNotFoundException("TicketOrder", orderId)
        if (order.status == OrderStatus.CONFIRMED) {
            return TicketOrderResult.of(order)
        }
        order.confirm(paymentId, order.lockedSeatIds)
        ticketOrderRepository.save(order)
        val event = eventRepository.findById(order.lockedEventId)
            ?: throw ResourceNotFoundException("Event", order.lockedEventId)
        domainEventPublisher.publish(
            TicketEvent.Issued(
                ticketOrderId = order.id,
                recipientUserId = order.userId,
                eventTitle = event.title,
            )
        )
        return TicketOrderResult.of(order)
    }

    @Transactional
    fun cancelOrder(orderId: Long) {
        val order = ticketOrderRepository.findById(orderId)
            ?: throw ResourceNotFoundException("TicketOrder", orderId)
        if (order.status == OrderStatus.CANCELLED) return
        order.cancel()
        ticketOrderRepository.save(order)
        val tickets = ticketRepository.findByTicketOrderId(orderId)
        tickets.forEach { ticket ->
            ticket.revoke()
            ticket.softDelete(null)
        }
        if (tickets.isNotEmpty()) ticketRepository.saveAll(tickets)
        registerSeatUnlockAfterCommit(order)
    }

    private fun registerSeatUnlockAfterCommit(order: TicketOrder) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            unlockSeats(order)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                unlockSeats(order)
            }
        })
    }

    private fun unlockSeats(order: TicketOrder) {
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

    fun deleteEvent(eventId: Long, deletedBy: Long) {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.requireDeletable()
        eventRepository.softDelete(eventId, deletedBy)
        seatRepository.softDeleteByEventId(eventId, deletedBy)
    }

    fun issueComplimentary(eventId: Long, seatId: Long, operatorUserId: Long): Ticket {
        val event = eventRepository.findById(eventId)
            ?: throw ResourceNotFoundException("Event", eventId)
        event.requireOwnedBy(operatorUserId)
        val ticket = Ticket.issueComplimentary(seatId)
        return ticketRepository.save(ticket)
    }

    fun aggregateTicketKpi(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): TicketKpiSummary {
        val summary = ticketOrderCustomRepository.aggregateTicketSales(ownerUserId, null, from, to)
        val complimentaryCount = ticketOrderCustomRepository.countComplimentaryByOwnerUserIdAndDateRange(ownerUserId, from, to)

        val totalCount = summary.totalTicketCount + summary.cancelledCount
        val refundRate = if (totalCount > 0) {
            BigDecimal(summary.cancelledCount).multiply(BigDecimal(100))
                .divide(BigDecimal(totalCount), 2, RoundingMode.HALF_UP)
        } else BigDecimal.ZERO

        return TicketKpiSummary(
            totalSoldCount = summary.totalTicketCount,
            refundRate = refundRate,
            complimentaryCount = complimentaryCount,
        )
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
