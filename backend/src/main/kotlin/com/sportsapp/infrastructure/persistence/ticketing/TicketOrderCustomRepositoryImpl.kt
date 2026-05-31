package com.sportsapp.infrastructure.persistence.ticketing

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.QEvent.event
import com.sportsapp.domain.ticketing.QSeat.seat
import com.sportsapp.domain.ticketing.QTicket.ticket
import com.sportsapp.domain.ticketing.QTicketOrder.ticketOrder
import com.sportsapp.domain.ticketing.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.TicketSalesSummary
import com.sportsapp.domain.ticketing.TicketStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.ZonedDateTime

@Repository
class TicketOrderCustomRepositoryImpl : TicketOrderCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary {
        val confirmedCount = queryFactory.select(ticket.count())
                                         .from(ticket)
                                         .join(ticket.ticketOrder, ticketOrder)
                                         .join(event).on(event.id.eq(ticketOrder.lockedEventId))
                                         .where(
                                             event.ownerId.eq(ownerUserId),
                                             eventId?.let { event.id.eq(it) },
                                             ticket.status.eq(TicketStatus.ISSUED),
                                             ticketOrder.createdAt.goe(from),
                                             ticketOrder.createdAt.loe(to),
                                         )
                                         .fetchOne() ?: 0L

        val revenue = queryFactory.select(seat.price.sum())
                                  .from(ticket)
                                  .join(ticket.ticketOrder, ticketOrder)
                                  .join(event).on(event.id.eq(ticketOrder.lockedEventId))
                                  .join(seat).on(seat.id.eq(ticket.seatId))
                                  .where(
                                      event.ownerId.eq(ownerUserId),
                                      eventId?.let { event.id.eq(it) },
                                      ticket.status.eq(TicketStatus.ISSUED),
                                      ticketOrder.createdAt.goe(from),
                                      ticketOrder.createdAt.loe(to),
                                  )
                                  .fetchOne() ?: BigDecimal.ZERO

        val cancelledCount = queryFactory.select(ticketOrder.count())
                                         .from(ticketOrder)
                                         .join(event).on(event.id.eq(ticketOrder.lockedEventId))
                                         .where(
                                             event.ownerId.eq(ownerUserId),
                                             eventId?.let { event.id.eq(it) },
                                             ticketOrder.status.eq(OrderStatus.CANCELLED),
                                             ticketOrder.createdAt.goe(from),
                                             ticketOrder.createdAt.loe(to),
                                         )
                                         .fetchOne() ?: 0L

        return TicketSalesSummary(
            totalTicketCount = confirmedCount,
            totalRevenue = revenue,
            cancelledCount = cancelledCount,
        )
    }

    override fun countComplimentaryByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long =
        queryFactory.select(ticket.count())
                    .from(ticket)
                    .join(seat).on(seat.id.eq(ticket.seatId))
                    .join(event).on(event.id.eq(seat.eventId))
                    .where(
                        event.ownerId.eq(ownerUserId),
                        ticket.ticketOrder.isNull,
                        ticket.status.eq(TicketStatus.ISSUED),
                        ticket.createdAt.goe(from),
                        ticket.createdAt.loe(to),
                    )
                    .fetchOne() ?: 0L
}
