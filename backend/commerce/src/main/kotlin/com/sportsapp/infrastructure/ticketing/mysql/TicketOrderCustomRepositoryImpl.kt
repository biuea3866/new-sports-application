package com.sportsapp.infrastructure.ticketing.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.QEvent.event
import com.sportsapp.domain.ticketing.entity.QSeat.seat
import com.sportsapp.domain.ticketing.entity.QTicket.ticket
import com.sportsapp.domain.ticketing.entity.QTicketOrder.ticketOrder
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSalesSummary
import com.sportsapp.domain.ticketing.entity.TicketStatus
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

    override fun findOrderIdsByEventOwnerUserId(ownerUserId: Long): List<Long> =
        queryFactory.select(ticketOrder.id)
                    .from(ticketOrder)
                    .join(event).on(event.id.eq(ticketOrder.lockedEventId))
                    .where(
                        event.ownerId.eq(ownerUserId),
                        event.deletedAt.isNull,
                        ticketOrder.deletedAt.isNull,
                    )
                    .fetch()

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

    // 좌석(lockedSeatIds)은 JSON 텍스트 컬럼(JsonStringType)이라 QueryDSL Tuple로 직접 선택하지
    // 않는다 — 엔티티 전체(ticketOrder)를 선택해 Hibernate가 커스텀 타입으로 정상 역직렬화하게
    // 하고, 좌석가/좌석요약은 이 결과의 lockedSeatIds로 별도 배치 조회한다(N+1 회피).
    override fun findBy(userId: Long): List<TicketOrderWithEventTitle> {
        val tuples = queryFactory
            .select(ticketOrder, event.title, event.deletedAt)
            .from(ticketOrder)
            .leftJoin(event).on(event.id.eq(ticketOrder.lockedEventId))
            .where(
                ticketOrder.userId.eq(userId),
                ticketOrder.deletedAt.isNull,
            )
            .fetch()

        val orders = tuples.map { requireNotNull(it.get(ticketOrder)) { "ticketOrder must not be null" } }
        val allSeatIds = orders.flatMap { it.lockedSeatIds }.distinct()
        val seatById = findSeatsByIds(allSeatIds).associateBy { it.id }

        return tuples.map { tuple ->
            val order = requireNotNull(tuple.get(ticketOrder)) { "ticketOrder must not be null" }
            val eventTitle = tuple.get(event.title)
            val eventDeletedAt = tuple.get(event.deletedAt)
            val lockedSeats = order.lockedSeatIds.mapNotNull { seatById[it] }
            TicketOrderWithEventTitle(
                ticketOrderId = order.id,
                status = order.status,
                eventTitle = if (eventTitle == null || eventDeletedAt != null) "" else eventTitle,
                paymentId = order.paymentId,
                createdAt = order.createdAt,
                totalAmount = lockedSeats.fold(BigDecimal.ZERO) { sum, seatEntity -> sum + seatEntity.price },
                seatSummary = buildSeatSummary(lockedSeats),
            )
        }
    }

    private fun findSeatsByIds(seatIds: List<Long>): List<Seat> {
        if (seatIds.isEmpty()) return emptyList()
        return queryFactory.selectFrom(seat).where(seat.id.`in`(seatIds)).fetch()
    }

    private fun buildSeatSummary(lockedSeats: List<Seat>): String {
        val representative = lockedSeats.firstOrNull() ?: return ""
        return if (lockedSeats.size > 1) {
            "${representative.displayLabel} 외 ${lockedSeats.size - 1}석"
        } else {
            representative.displayLabel
        }
    }
}
