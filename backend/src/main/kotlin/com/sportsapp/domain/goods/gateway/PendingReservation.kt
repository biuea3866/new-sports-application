package com.sportsapp.domain.goods.gateway

/**
 * [DropReservationStore.scanStaleReservations]가 반환하는 예약 마커 스냅샷 (FIX-04, 언더셀 대사).
 *
 * 마커 생성 시 함께 저장된 [userId]·[quantity]를 [DropReservationStore.restoreOrphanedReservation]
 * 호출에 그대로 사용한다 — 이 값이 없으면 buyer 카운터(1인 한도)를 올바르게 복원할 수 없다.
 */
data class PendingReservation(
    val idempotencyKey: String,
    val userId: Long,
    val quantity: Int,
)
