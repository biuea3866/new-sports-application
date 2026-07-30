package com.sportsapp.domain.mcp.service

import com.sportsapp.domain.mcp.dto.McpUsageAnalyticsResult
import com.sportsapp.domain.mcp.dto.ToolLatencyStat
import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.repository.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.repository.McpAuditLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class McpAuditLogDomainService(
    private val mcpAuditLogRepository: McpAuditLogRepository,
    private val mcpAuditLogCustomRepository: McpAuditLogCustomRepository,
) {
    fun listByUser(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        pageable: Pageable,
    ): Page<McpAuditLog> =
        mcpAuditLogRepository.findByUserIdAndCalledAtBetween(userId, from, to, pageable)

    fun recordToolInvocation(auditLog: McpAuditLog) {
        mcpAuditLogRepository.save(auditLog)
    }

    fun aggregateUsageAnalytics(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): McpUsageAnalyticsResult {
        val dailyStats = mcpAuditLogCustomRepository.findDailyUsageStats(userId, from, to)
        val toolStats = mcpAuditLogCustomRepository.findToolCallStats(userId, from, to)
        val errorRateStat = mcpAuditLogCustomRepository.findErrorRateStat(userId, from, to)
        val latencyByTool = mcpAuditLogCustomRepository.findLatencyMsByTool(userId, from, to)
        val tokenStats = mcpAuditLogCustomRepository.findTokenUsageStats(userId, from, to, limit = 20)
        val toolLatencyStats = computeP95LatencyStats(latencyByTool)
        return McpUsageAnalyticsResult(
            dailyStats = dailyStats,
            toolCallStats = toolStats,
            errorRateStat = errorRateStat,
            toolLatencyStats = toolLatencyStats,
            tokenUsageStats = tokenStats,
        )
    }

    /**
     * MVP: application 레벨 정렬 기반 P95 산출.
     * latencyByTool: toolName → latencyMs 목록 (DB에서 raw 조회).
     * 주의: 대용량 데이터에서는 DB 레벨 percentile 집계로 전환 필요 (PoC 미정).
     */
    private fun computeP95LatencyStats(latencyByTool: Map<String, List<Int>>): List<ToolLatencyStat> {
        return latencyByTool.map { (toolName, latencies) ->
            val sorted = latencies.sorted()
            val p95Index = ((sorted.size - 1) * 0.95).toInt()
            ToolLatencyStat(toolName = toolName, p95LatencyMs = sorted.getOrElse(p95Index) { 0 })
        }.sortedByDescending { it.p95LatencyMs }
    }
}

