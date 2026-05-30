package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.DailyCallCount
import com.sportsapp.domain.mcp.DailyUsageStat
import com.sportsapp.domain.mcp.ErrorRateStat
import com.sportsapp.domain.mcp.TokenCallStats
import com.sportsapp.domain.mcp.TokenUsageStat
import com.sportsapp.domain.mcp.ToolCallStat
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

    fun findDailyUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyUsageStat>

    fun findToolCallStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<ToolCallStat>

    fun findErrorRateStat(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): ErrorRateStat

    fun findLatencyMsByTool(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Map<String, List<Int>>

    fun findTokenUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<TokenUsageStat>
}
