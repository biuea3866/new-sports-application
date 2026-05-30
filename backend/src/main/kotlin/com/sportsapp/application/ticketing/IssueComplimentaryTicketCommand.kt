package com.sportsapp.application.ticketing

data class IssueComplimentaryTicketCommand(
    val eventId: Long,
    val seatId: Long,
    val operatorUserId: Long,
)
