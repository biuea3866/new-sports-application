package com.sportsapp.domain.partner.entity

enum class PartnerStatus {
    ACTIVE,
    SUSPENDED;

    fun canTransitTo(target: PartnerStatus): Boolean = when (this) {
        ACTIVE -> target == SUSPENDED
        SUSPENDED -> target == ACTIVE
    }
}
