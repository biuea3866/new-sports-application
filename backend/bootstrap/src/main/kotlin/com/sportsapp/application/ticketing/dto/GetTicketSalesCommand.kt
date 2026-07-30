package com.sportsapp.application.ticketing.dto

import java.time.ZonedDateTime

data class GetTicketSalesCommand(
    val ownerUserId: Long,
    val eventId: Long?,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
