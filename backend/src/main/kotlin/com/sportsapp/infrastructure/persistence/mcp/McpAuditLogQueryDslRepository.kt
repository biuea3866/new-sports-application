package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.DailyCallCount
import com.sportsapp.domain.mcp.TokenCallStats
import java.time.ZonedDateTime

interface McpAuditLogQueryDslRepository {
    fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats>

    fun findDailyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyCallCount>

    fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long
}
