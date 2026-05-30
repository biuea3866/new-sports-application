package com.sportsapp.domain.mcp

/**
 * 하루(날짜)의 tool 호출 수 집계 결과.
 *
 * @param dayOfMonth 일자 (1~31)
 * @param callCount 해당 날짜 호출 수
 */
data class DailyCallCount(
    val dayOfMonth: Int,
    val callCount: Long,
)
