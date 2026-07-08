package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.domain.mcp.dto.DailyCallCount
import com.sportsapp.domain.mcp.dto.DailyUsageStat
import com.sportsapp.domain.mcp.dto.ErrorRateStat
import com.sportsapp.domain.mcp.repository.TokenCallStats
import com.sportsapp.domain.mcp.dto.TokenUsageStat
import com.sportsapp.domain.mcp.dto.ToolCallStat
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
