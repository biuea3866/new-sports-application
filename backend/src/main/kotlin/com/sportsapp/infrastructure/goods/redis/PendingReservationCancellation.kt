package com.sportsapp.infrastructure.goods.redis

/**
 * [DropReservationCompensatorImpl]이 롤백된 시도의 취소 후보를 [org.springframework.retry.RetryContext]에
 * 남길 때 사용하는 값 객체 (FIX-02, code-review p1) — [DropReservationCompensationRetryListener]가
 * 재시도 시퀀스 종료 시점에 이 값을 읽어 실제 취소를 수행한다.
 */
data class PendingReservationCancellation(
    val dropId: Long,
    val userId: Long,
    val quantity: Int,
    val idempotencyKey: String,
)
