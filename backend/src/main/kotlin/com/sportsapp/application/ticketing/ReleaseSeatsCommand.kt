package com.sportsapp.application.ticketing

data class ReleaseSeatsCommand(
    val eventId: Long,
    val seatIds: List<Long>,
    val userId: Long,
)
