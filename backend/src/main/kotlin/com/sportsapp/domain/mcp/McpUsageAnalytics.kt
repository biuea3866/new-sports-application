package com.sportsapp.domain.mcp

import java.time.ZonedDateTime

/**
 * 일별 tool 호출 수 (대시보드 일별 시계열 차트용).
 * date: ISO 8601 날짜 문자열 (yyyy-MM-dd, UTC 기준).
 */
data class DailyUsageStat(
    val date: String,
    val toolName: String,
    val callCount: Long,
)

/**
 * tool별 총 호출 수 (TOP N 차트용).
 */
data class ToolCallStat(
    val toolName: String,
    val callCount: Long,
)

/**
 * 에러율 집계 결과 (statusCode >= 400 기준).
 */
data class ErrorRateStat(
    val totalCount: Long,
    val errorCount: Long,
) {
    val errorRatePercent: Double
        get() = if (totalCount == 0L) 0.0 else errorCount.toDouble() / totalCount.toDouble() * 100.0
}

/**
 * tool별 latency 집계 결과.
 *
 * MVP P95 계산 방식: application 레벨 정렬 기반 근사 — DB에서 모든 latency 값을 조회 후
 * 정렬하여 95번째 백분위수를 산출. 데이터 양이 많을 경우(수백만 건 이상) 정확한 percentile을
 * 위한 DB 레벨 집계(Histogram / t-digest)가 필요하나, MVP 단계에서는 PoC 미정이므로
 * 단순 정렬 방식을 사용하고 쿼리 범위를 기간 필터로 제한한다.
 */
data class ToolLatencyStat(
    val toolName: String,
    val p95LatencyMs: Int,
)

/**
 * 토큰별 사용 현황 (TOP 20 표 용).
 */
data class TokenUsageStat(
    val tokenId: Long,
    val callCount: Long,
    val errorCount: Long,
    val lastCalledAt: ZonedDateTime?,
) {
    val errorRatePercent: Double
        get() = if (callCount == 0L) 0.0 else errorCount.toDouble() / callCount.toDouble() * 100.0
}
