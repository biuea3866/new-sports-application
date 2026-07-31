package com.sportsapp.domain.mcp.repository

import java.time.ZonedDateTime
import com.sportsapp.domain.mcp.dto.DailyCallCount
import com.sportsapp.domain.mcp.dto.DailyUsageStat
import com.sportsapp.domain.mcp.dto.ErrorRateStat
import com.sportsapp.domain.mcp.dto.TokenUsageStat
import com.sportsapp.domain.mcp.dto.ToolCallStat

data class TokenCallStats(
    val tokenId: Long,
    val callCount: Long,
)

interface McpAuditLogCustomRepository {
    fun findCallStatsByTokenIdIn(
        tokenIds: List<Long>,
        from: ZonedDateTime,
    ): List<TokenCallStats>

    /**
     * 7일 베이스라인 계산용: 특정 토큰의 [from]..[to] 기간 일별 호출 수 집계.
     * 반환 결과는 날짜(day) 단위 row — computeBaselineAverage 에서 일평균 산출.
     */
    fun findDailyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyCallCount>

    /**
     * 현재 1시간 호출 수 조회: [from] 이후 호출 수.
     */
    fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long

    // ──────────────────────────────────────────────
    // FR-01 MCP 사용 분석 집계 쿼리
    // ──────────────────────────────────────────────

    /**
     * [userId] 의 [from]..[to] 기간 일별·tool별 호출 수 집계.
     * 반환 결과: (날짜, toolName, callCount) 목록 — 일별 시계열 차트용.
     */
    fun findDailyUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<DailyUsageStat>

    /**
     * [userId] 의 [from]..[to] 기간 tool별 총 호출 수 집계.
     * 반환 결과: callCount 내림차순 정렬 — TOP N 차트용.
     */
    fun findToolCallStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<ToolCallStat>

    /**
     * [userId] 의 [from]..[to] 기간 전체 + 에러(statusCode >= 400) 호출 수 집계.
     */
    fun findErrorRateStat(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): ErrorRateStat

    /**
     * [userId] 의 [from]..[to] 기간 tool별 latency_ms 전체 목록 조회.
     * MVP: application 레벨 정렬 기반 P95 산출용 raw 데이터.
     * 주의: 대용량 데이터(수백만 건)에서는 DB 레벨 percentile 집계로 전환 필요 (PoC 미정).
     */
    fun findLatencyMsByTool(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Map<String, List<Int>>

    /**
     * [userId] 의 [from]..[to] 기간 tokenId별 호출 수·에러 수·마지막 호출 시각 집계.
     * 반환 결과: callCount 내림차순 정렬 TOP 20 — 토큰별 사용 TOP 표용.
     */
    fun findTokenUsageStats(
        userId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
        limit: Int,
    ): List<TokenUsageStat>
}
