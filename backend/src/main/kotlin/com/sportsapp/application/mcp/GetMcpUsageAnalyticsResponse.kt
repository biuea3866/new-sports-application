package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpUsageAnalyticsResult
import java.time.ZonedDateTime

data class GetMcpUsageAnalyticsResponse(
    /** 일별·tool별 호출 수 시계열 (차트 1) */
    val dailyStats: List<DailyUsageStatResponse>,
    /** tool별 호출 TOP (차트 2) */
    val toolCallStats: List<ToolCallStatResponse>,
    /** 에러율 (차트 3) */
    val errorRateStat: ErrorRateStatResponse,
    /** tool별 P95 latency (차트 4) */
    val toolLatencyStats: List<ToolLatencyStatResponse>,
    /** 토큰별 사용 TOP 20 (표) */
    val tokenUsageStats: List<TokenUsageStatResponse>,
) {
    companion object {
        fun of(result: McpUsageAnalyticsResult): GetMcpUsageAnalyticsResponse =
            GetMcpUsageAnalyticsResponse(
                dailyStats = result.dailyStats.map {
                    DailyUsageStatResponse(date = it.date, toolName = it.toolName, callCount = it.callCount)
                },
                toolCallStats = result.toolCallStats.map {
                    ToolCallStatResponse(toolName = it.toolName, callCount = it.callCount)
                },
                errorRateStat = ErrorRateStatResponse(
                    totalCount = result.errorRateStat.totalCount,
                    errorCount = result.errorRateStat.errorCount,
                    errorRatePercent = result.errorRateStat.errorRatePercent,
                ),
                toolLatencyStats = result.toolLatencyStats.map {
                    ToolLatencyStatResponse(toolName = it.toolName, p95LatencyMs = it.p95LatencyMs)
                },
                tokenUsageStats = result.tokenUsageStats.map {
                    TokenUsageStatResponse(
                        tokenId = it.tokenId,
                        callCount = it.callCount,
                        errorCount = it.errorCount,
                        errorRatePercent = it.errorRatePercent,
                        lastCalledAt = it.lastCalledAt,
                    )
                },
            )
    }
}

/** date: ISO 8601 날짜 문자열 (yyyy-MM-dd, UTC 기준) */
data class DailyUsageStatResponse(
    val date: String,
    val toolName: String,
    val callCount: Long,
)

data class ToolCallStatResponse(
    val toolName: String,
    val callCount: Long,
)

data class ErrorRateStatResponse(
    val totalCount: Long,
    val errorCount: Long,
    val errorRatePercent: Double,
)

data class ToolLatencyStatResponse(
    val toolName: String,
    /** MVP: application 레벨 정렬 기반 P95 (ms). 대용량 시 DB 레벨 percentile로 전환 필요 */
    val p95LatencyMs: Int,
)

data class TokenUsageStatResponse(
    val tokenId: Long,
    val callCount: Long,
    val errorCount: Long,
    val errorRatePercent: Double,
    val lastCalledAt: ZonedDateTime?,
)
