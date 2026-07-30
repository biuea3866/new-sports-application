package com.sportsapp.domain.ticketing.entity

import com.sportsapp.domain.ticketing.exception.InvalidEventStateException

enum class EventStatus {
    SCHEDULED,
    OPEN,
    CLOSED,
    CANCELLED;

    fun canTransitTo(target: EventStatus): Boolean = when (this) {
        SCHEDULED -> target == OPEN
        OPEN -> target == CLOSED
        CLOSED -> false
        CANCELLED -> false
    }

    fun requireCanTransitTo(target: EventStatus) {
        if (!canTransitTo(target)) {
            throw InvalidEventStateException("Cannot transit from $this to $target")
        }
    }
}
