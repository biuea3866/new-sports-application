package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSalesSummary
import java.time.ZonedDateTime

interface TicketOrderCustomRepository {
    fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary

    fun countComplimentaryByOwnerUserIdAndDateRange(
        ownerUserId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long

    fun findBy(userId: Long): List<TicketOrderWithEventTitle>

    /**
     * 주최자(파트너)가 연 경기에 걸린 티켓 주문 id — 포털 매출 내역이 소비한다.
     * 티켓 주문은 경기 하나에 귀속되므로(lockedEventId) 판매자가 단일하게 정해진다.
     */
    fun findOrderIdsByEventOwnerUserId(ownerUserId: Long): List<Long>
}
