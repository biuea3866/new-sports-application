package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.LimitedDropStatsResult

/**
 * 한정판 회차 집계 응답(`GET /limited-drops/{dropId}/stats`, FR-9).
 */
data class LimitedDropStatsResponse(
    val successCount: Long,
    val soldOutRejectCount: Long,
    val tooEarlyRejectCount: Long,
) {
    companion object {
        fun of(result: LimitedDropStatsResult): LimitedDropStatsResponse = LimitedDropStatsResponse(
            successCount = result.successCount,
            soldOutRejectCount = result.soldOutRejectCount,
            tooEarlyRejectCount = result.tooEarlyRejectCount,
        )
    }
}
