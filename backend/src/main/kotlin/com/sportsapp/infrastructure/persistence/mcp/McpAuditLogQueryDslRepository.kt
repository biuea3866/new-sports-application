package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.TokenCallStats
import java.time.ZonedDateTime

interface McpAuditLogQueryDslRepository {
    fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats>
}
