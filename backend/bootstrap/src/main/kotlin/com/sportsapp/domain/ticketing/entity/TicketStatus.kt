package com.sportsapp.domain.ticketing.entity

import com.sportsapp.domain.ticketing.exception.InvalidTicketStateException

enum class TicketStatus {
    ISSUED,
    REVOKED;

    fun canTransitTo(target: TicketStatus): Boolean = when (this) {
        ISSUED -> target == REVOKED
        REVOKED -> false
    }

    fun requireCanTransitTo(target: TicketStatus) {
        if (!canTransitTo(target)) {
            throw InvalidTicketStateException("Cannot transit from $this to $target")
        }
    }
}
