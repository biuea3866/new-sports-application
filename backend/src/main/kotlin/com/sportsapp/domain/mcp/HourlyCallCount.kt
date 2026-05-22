package com.sportsapp.domain.mcp

/**
 * 특정 시간대(hour)의 tool 호출 수 집계 결과.
 *
 * @param hour 시간 (0~23)
 * @param callCount 해당 시간대 호출 수
 */
data class HourlyCallCount(
    val hour: Int,
    val callCount: Long,
)
