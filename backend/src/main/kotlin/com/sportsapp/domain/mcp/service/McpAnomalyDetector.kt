package com.sportsapp.domain.mcp.service

import java.time.ZonedDateTime

/**
 * MCP tool 호출 패턴의 비정상 여부를 판정하는 순수 도메인 클래스.
 *
 * 판정 기준:
 * - 현재 1시간 호출 수가 7일 베이스라인 평균의 [SPIKE_RATIO]배 이상이면 비정상
 * - 베이스라인 평균이 0이더라도 [MIN_ABSOLUTE_THRESHOLD] 이상이면 비정상 (절대값 임계)
 */
class McpAnomalyDetector {

    companion object {
        const val SPIKE_RATIO = 2.0
        const val MIN_ABSOLUTE_THRESHOLD = 50
        const val COLD_START_DAYS = 14L
        const val BASELINE_WINDOW_DAYS = 7L
    }

    /**
     * 비정상 급증 판정.
     *
     * @param baselineAverage 7일 베이스라인 평균 호출 수
     * @param currentHourCount 현재 1시간 호출 수
     * @return true = 비정상
     */
    fun isAnomaly(baselineAverage: Double, currentHourCount: Long): Boolean {
        if (baselineAverage <= 0.0) {
            return currentHourCount >= MIN_ABSOLUTE_THRESHOLD
        }
        return currentHourCount >= baselineAverage * SPIKE_RATIO
    }

    /**
     * cold-start 판정. 토큰 생성 후 [COLD_START_DAYS]일 미만이면 학습 기간으로 간주.
     *
     * @param tokenCreatedAt 토큰 생성 시각
     * @return true = cold-start (베이스라인 미적용)
     */
    fun isColdStart(tokenCreatedAt: ZonedDateTime): Boolean {
        val cutoff = ZonedDateTime.now().minusDays(COLD_START_DAYS)
        return tokenCreatedAt.isAfter(cutoff)
    }

    /**
     * 일별 호출 수 목록으로 베이스라인 평균 계산.
     */
    fun computeBaselineAverage(dailyCounts: List<Long>): Double {
        if (dailyCounts.isEmpty()) return 0.0
        return dailyCounts.average()
    }
}
