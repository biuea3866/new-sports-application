package com.sportsapp.domain.ticketing

import java.time.ZonedDateTime

interface CustomTicketOrderRepository {
    fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary
}
