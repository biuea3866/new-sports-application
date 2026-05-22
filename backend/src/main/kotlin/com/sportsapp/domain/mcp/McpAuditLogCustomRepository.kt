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

    /**
     * 7일 베이스라인 계산용: 특정 토큰의 [from]..[to] 기간 시간대별 호출 수 집계.
     */
    fun findHourlyCallCountsForBaseline(
        tokenId: Long,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): List<HourlyCallCount>

    /**
     * 현재 1시간 호출 수 조회: [from] 이후 호출 수.
     */
    fun findCurrentHourCallCount(
        tokenId: Long,
        from: ZonedDateTime,
    ): Long
}
