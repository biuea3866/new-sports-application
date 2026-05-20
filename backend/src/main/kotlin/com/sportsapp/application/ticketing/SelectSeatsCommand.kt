package com.sportsapp.application.ticketing

data class SelectSeatsCommand(
    val eventId: Long,
    val seatIds: List<Long>,
    val userId: Long,
) {
    init {
        require(seatIds.isNotEmpty()) { "seatIds must not be empty" }
    }
}
