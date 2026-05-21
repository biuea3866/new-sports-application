package com.sportsapp.domain.ticketing

import java.time.ZonedDateTime

interface TicketOrderCustomRepository {
    fun aggregateTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): TicketSalesSummary
}
