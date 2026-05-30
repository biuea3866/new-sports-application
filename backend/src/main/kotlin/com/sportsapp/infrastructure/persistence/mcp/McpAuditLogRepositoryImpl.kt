package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.DailyCallCount
import com.sportsapp.domain.mcp.DailyUsageStat
import com.sportsapp.domain.mcp.ErrorRateStat
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.mcp.TokenCallStats
import com.sportsapp.domain.mcp.TokenUsageStat
import com.sportsapp.domain.mcp.ToolCallStat
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class McpAuditLogRepositoryImpl(
    private val mcpAuditLogJpaRepository: McpAuditLogJpaRepository,
) : McpAuditLogRepository, McpAuditLogCustomRepository {

    override fun save(auditLog: McpAuditLog): McpAuditLog =
        mcpAuditLogJpaRepository.save(auditLog)

    override fun findByUserIdAndCalledAtBetween(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<McpAuditLog> =
        mcpAuditLogJpaRepository.findAllByUserIdAndCalledAtBetween(userId, from, to, pageable)

    override fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats> =
        mcpAuditLogJpaRepository.findCallStatsByTokenIdIn(tokenIds, from)

    override fun findDailyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyCallCount> =
        mcpAuditLogJpaRepository.findDailyCallCountsForBaseline(tokenId, from, to)

    override fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long =
        mcpAuditLogJpaRepository.findCurrentHourCallCount(tokenId, from)

    override fun findDailyUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyUsageStat> =
        mcpAuditLogJpaRepository.findDailyUsageStats(userId, from, to)

    override fun findToolCallStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<ToolCallStat> =
        mcpAuditLogJpaRepository.findToolCallStats(userId, from, to)

    override fun findErrorRateStat(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): ErrorRateStat =
        mcpAuditLogJpaRepository.findErrorRateStat(userId, from, to)

    override fun findLatencyMsByTool(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Map<String, List<Int>> =
        mcpAuditLogJpaRepository.findLatencyMsByTool(userId, from, to)

    override fun findTokenUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<TokenUsageStat> =
        mcpAuditLogJpaRepository.findTokenUsageStats(userId, from, to, limit)
}
