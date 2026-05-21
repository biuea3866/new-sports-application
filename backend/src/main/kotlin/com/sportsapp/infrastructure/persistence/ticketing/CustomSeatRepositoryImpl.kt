package com.sportsapp.infrastructure.persistence.ticketing

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.CustomSeatRepository
import com.sportsapp.domain.ticketing.QEvent
import com.sportsapp.domain.ticketing.QSeat
import com.sportsapp.domain.ticketing.QTicket
import com.sportsapp.domain.ticketing.TicketStatus
import org.springframework.stereotype.Component

@Component
class CustomSeatRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CustomSeatRepository {

    override fun countSoldByEventId(eventId: Long): Long {
        val seat = QSeat.seat
        val ticket = QTicket.ticket
        return queryFactory.select(ticket.count())
                           .from(ticket)
                           .join(seat).on(seat.id.eq(ticket.seatId))
                           .where(
                               seat.eventId.eq(eventId),
                               seat.deletedAt.isNull,
                               ticket.deletedAt.isNull,
                               ticket.status.eq(TicketStatus.ISSUED),
                           )
                           .fetchOne() ?: 0L
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
