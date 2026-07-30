package com.sportsapp.domain.booking.entity

enum class SlotStatus {
    OPEN,
    CLOSED;

    fun canTransitTo(target: SlotStatus): Boolean = when (this) {
        OPEN -> target == CLOSED
        CLOSED -> target == OPEN
    }
}
