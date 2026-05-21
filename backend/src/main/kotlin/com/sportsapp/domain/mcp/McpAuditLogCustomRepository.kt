package com.sportsapp.domain.mcp

import java.time.ZonedDateTime

data class TokenCallStats(
    val tokenId: Long,
    val callCount: Long,
)

interface McpAuditLogCustomRepository {
    fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats>
}
