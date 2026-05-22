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
}
