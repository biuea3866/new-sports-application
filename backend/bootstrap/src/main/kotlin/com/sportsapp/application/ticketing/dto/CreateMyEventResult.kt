package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event

data class CreateMyEventResult(
    val eventId: Long,
    val seatCount: Int,
) {
    companion object {
        fun of(event: Event, seatCount: Int): CreateMyEventResult = CreateMyEventResult(
            eventId = event.id,
            seatCount = seatCount,
        )
    }
}
