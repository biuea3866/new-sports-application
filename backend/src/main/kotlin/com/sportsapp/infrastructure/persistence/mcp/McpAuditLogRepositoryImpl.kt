package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.domain.mcp.HourlyCallCount
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.McpAuditLogRepository
import com.sportsapp.domain.mcp.TokenCallStats
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

    override fun findHourlyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<HourlyCallCount> =
        mcpAuditLogJpaRepository.findHourlyCallCountsForBaseline(tokenId, from, to)

    override fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long =
        mcpAuditLogJpaRepository.findCurrentHourCallCount(tokenId, from)
}
