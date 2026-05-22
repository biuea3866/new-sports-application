package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.HourlyCallCount
import com.sportsapp.domain.mcp.TokenCallStats
import java.time.ZonedDateTime

interface McpAuditLogQueryDslRepository {
    fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats>

    fun findHourlyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<HourlyCallCount>

    fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long
}
