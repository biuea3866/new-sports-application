package com.sportsapp.domain.goods.gateway

/**
 * FR-9 거부 집계 스냅샷. [DropReservationStore.rejectCounts]의 반환 타입 — Redis 카운터 기반
 * 휘발성 운영 지표(재기동 시 리셋될 수 있음)다.
 */
data class RejectCounts(
    val soldOutCount: Long,
    val tooEarlyCount: Long,
)
