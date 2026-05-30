package com.sportsapp.domain.mcp

enum class McpAnomalyEventStatus {
    OPEN,
    RESOLVED,
    FALSE_POSITIVE,
    ;

    fun canTransitTo(target: McpAnomalyEventStatus): Boolean = when (this) {
        OPEN -> target == RESOLVED || target == FALSE_POSITIVE
        RESOLVED -> false
        FALSE_POSITIVE -> false
    }
}
