package com.sportsapp.application.ticketing.dto

data class IssueComplimentaryTicketCommand(
    val eventId: Long,
    val seatId: Long,
    val operatorUserId: Long,
)
