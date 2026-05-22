package com.sportsapp.application.ticketing

import java.time.ZonedDateTime

data class GetTicketSalesCommand(
    val ownerUserId: Long,
    val eventId: Long?,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
