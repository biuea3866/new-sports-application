package com.sportsapp.domain.mcp

enum class McpTokenStatus {
    ACTIVE,
    SUSPENDED,
    REVOKED;

    fun canTransitTo(target: McpTokenStatus): Boolean = when (this) {
        ACTIVE -> target == SUSPENDED || target == REVOKED
        SUSPENDED -> target == ACTIVE || target == REVOKED
        REVOKED -> false
    }
}
