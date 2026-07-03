package com.sportsapp.application.goods.dto

/**
 * 한정판 회차 구매 시도 결과 집계 (FR-9).
 */
data class LimitedDropStatsResult(
    val successCount: Long,
    val soldOutRejectCount: Long,
    val tooEarlyRejectCount: Long,
)
