package com.sportsapp.infrastructure.ticketing.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.repository.SeatCustomRepository
import com.sportsapp.domain.ticketing.entity.QEvent
import com.sportsapp.domain.ticketing.entity.QSeat
import com.sportsapp.domain.ticketing.entity.QTicket
import com.sportsapp.domain.ticketing.entity.TicketStatus
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class SeatCustomRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : SeatCustomRepository {

    override fun findSoldSeatIdsByEventId(eventId: Long): Set<Long> {
        val seat = QSeat.seat
        val ticket = QTicket.ticket
        return queryFactory.select(ticket.seatId)
                           .from(ticket)
                           .join(seat).on(seat.id.eq(ticket.seatId))
                           .where(
                               seat.eventId.eq(eventId),
                               seat.deletedAt.isNull,
                               ticket.deletedAt.isNull,
                               ticket.status.eq(TicketStatus.ISSUED),
                           )
                           .fetch()
                           .toSet()
    }

    override fun findMinPriceByEventIds(eventIds: List<Long>): Map<Long, BigDecimal> {
        if (eventIds.isEmpty()) return emptyMap()
        val seat = QSeat.seat
        return queryFactory.select(seat.eventId, seat.price.min())
                           .from(seat)
                           .where(
                               seat.eventId.`in`(eventIds),
                               seat.deletedAt.isNull,
                           )
                           .groupBy(seat.eventId)
                           .fetch()
                           .mapNotNull { row ->
                               val eventId = row.get(seat.eventId) ?: return@mapNotNull null
                               val minPrice = row.get(seat.price.min()) ?: return@mapNotNull null
                               eventId to minPrice
                           }
                           .toMap()
    }

    override fun countSeatsByEventIds(eventIds: List<Long>): Map<Long, Long> {
        if (eventIds.isEmpty()) return emptyMap()
        val seat = QSeat.seat
        return queryFactory.select(seat.eventId, seat.count())
                           .from(seat)
                           .where(
                               seat.eventId.`in`(eventIds),
                               seat.deletedAt.isNull,
                           )
                           .groupBy(seat.eventId)
                           .fetch()
                           .mapNotNull { row ->
                               val eventId = row.get(seat.eventId) ?: return@mapNotNull null
                               val seatCount = row.get(seat.count()) ?: return@mapNotNull null
                               eventId to seatCount
                           }
                           .toMap()
    }

    override fun countSoldSeatsByEventIds(eventIds: List<Long>): Map<Long, Long> {
        if (eventIds.isEmpty()) return emptyMap()
        val seat = QSeat.seat
        val ticket = QTicket.ticket
        return queryFactory.select(seat.eventId, ticket.count())
                           .from(ticket)
                           .join(seat).on(seat.id.eq(ticket.seatId))
                           .where(
                               seat.eventId.`in`(eventIds),
                               seat.deletedAt.isNull,
                               ticket.deletedAt.isNull,
                               ticket.status.eq(TicketStatus.ISSUED),
                           )
                           .groupBy(seat.eventId)
                           .fetch()
                           .mapNotNull { row ->
                               val eventId = row.get(seat.eventId) ?: return@mapNotNull null
                               val soldCount = row.get(ticket.count()) ?: return@mapNotNull null
                               eventId to soldCount
                           }
                           .toMap()
    }

    override fun sumTotalSeatsByOwnerId(ownerId: Long): Long {
        val seat = QSeat.seat
        val event = QEvent.event
        return queryFactory.select(seat.count())
                           .from(seat)
                           .join(event).on(event.id.eq(seat.eventId))
                           .where(
                               event.ownerId.eq(ownerId),
                               event.deletedAt.isNull,
                               seat.deletedAt.isNull,
                           )
                           .fetchOne() ?: 0L
    }

    override fun sumSoldSeatsByOwnerId(ownerId: Long): Long {
        val seat = QSeat.seat
        val ticket = QTicket.ticket
        val event = QEvent.event
        return queryFactory.select(ticket.count())
                           .from(ticket)
                           .join(seat).on(seat.id.eq(ticket.seatId))
                           .join(event).on(event.id.eq(seat.eventId))
                           .where(
                               event.ownerId.eq(ownerId),
                               event.deletedAt.isNull,
                               seat.deletedAt.isNull,
                               ticket.deletedAt.isNull,
                               ticket.status.eq(TicketStatus.ISSUED),
                           )
                           .fetchOne() ?: 0L
    }
}
