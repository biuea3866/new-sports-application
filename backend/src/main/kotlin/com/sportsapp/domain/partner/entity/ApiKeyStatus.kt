package com.sportsapp.domain.partner.entity

enum class ApiKeyStatus {
    ACTIVE,
    REVOKED;

    fun canTransitTo(target: ApiKeyStatus): Boolean = when (this) {
        ACTIVE -> target == REVOKED
        REVOKED -> false
    }
}
