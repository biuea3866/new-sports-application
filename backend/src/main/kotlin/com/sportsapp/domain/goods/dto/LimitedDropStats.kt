package com.sportsapp.domain.goods.dto

/**
 * 한정판 회차 구매 시도 집계 (FR-9, 도메인 계층 산출물).
 * application 계층은 이를 [com.sportsapp.application.goods.dto.LimitedDropStatsResult]로 매핑한다.
 */
data class LimitedDropStats(
    val successCount: Long,
    val soldOutRejectCount: Long,
    val tooEarlyRejectCount: Long,
)
