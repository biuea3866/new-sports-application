package com.sportsapp.domain.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.Seat

data class EventSalesInfo(
    val event: Event,
    val seats: List<Seat>,
    val soldCount: Long,
) {
    val availableCount: Long get() = seats.size - soldCount
}
