package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.dto.LimitedDropStats

/**
 * 한정판 회차 구매 시도 결과 집계 (FR-9).
 */
data class LimitedDropStatsResult(
    val successCount: Long,
    val soldOutRejectCount: Long,
    val tooEarlyRejectCount: Long,
) {
    companion object {
        fun of(stats: LimitedDropStats): LimitedDropStatsResult = LimitedDropStatsResult(
            successCount = stats.successCount,
            soldOutRejectCount = stats.soldOutRejectCount,
            tooEarlyRejectCount = stats.tooEarlyRejectCount,
        )
    }
}
