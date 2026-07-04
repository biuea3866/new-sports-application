package com.sportsapp.domain.goods.gateway

/**
 * [DropReservationStore.reserve] 원자 판정의 결과.
 *
 * 판정 순서(FR-8 → FR-6)는 구현체([DropReservationStore.reserve] KDoc 참고) 책임이며,
 * 이 타입은 판정 결과만 표현한다.
 *
 * 완충(FR-7) 판정은 이 타입에 포함하지 않는다 — Redis 장애 시 fail-open 경로도 완충 게이트를
 * 반드시 통과해야 하므로(코드 리뷰 p1), [DropReservationStore.tryAcquireThrottle]로 reserve와
 * 독립적으로 판정한다.
 */
sealed interface ReservationResult {

    /** 입장 게이트·1인 한도를 모두 통과했다. */
    data object Admitted : ReservationResult

    /** 동일 idempotencyKey 로 이미 처리된 요청이다 (멱등 재시도). 재-차감하지 않는다. */
    data object AlreadyReserved : ReservationResult

    /** FR-8: 재고가 이미 소진되어 즉시 거부됐다. DB 에는 도달하지 않는다. */
    data object SoldOut : ReservationResult

    /** FR-6: 사용자의 1인 구매 한도를 초과했다. [limit] 은 초과 기준이 된 한도값이다. */
    data class PerUserLimitExceeded(val limit: Int) : ReservationResult
}
