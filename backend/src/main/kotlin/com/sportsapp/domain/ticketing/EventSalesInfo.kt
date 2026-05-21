package com.sportsapp.domain.ticketing

data class EventSalesInfo(
    val event: Event,
    val seats: List<Seat>,
    val soldCount: Long,
) {
    val availableCount: Long get() = seats.size - soldCount
}
