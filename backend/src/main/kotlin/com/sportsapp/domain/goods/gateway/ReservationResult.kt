package com.sportsapp.domain.goods.gateway

/**
 * [DropReservationStore.reserve] 원자 판정의 결과.
 *
 * 판정 순서(FR-8 → FR-7 → FR-6)는 구현체([DropReservationStore.reserve] KDoc 참고) 책임이며,
 * 이 타입은 판정 결과만 표현한다.
 */
sealed interface ReservationResult {

    /** 입장 게이트·완충·1인 한도를 모두 통과했다. 호출자는 완충 permit 을 보유한 상태다. */
    data object Admitted : ReservationResult

    /** 동일 idempotencyKey 로 이미 처리된 요청이다 (멱등 재시도). 재-차감하지 않는다. */
    data object AlreadyReserved : ReservationResult

    /** FR-8: 재고가 이미 소진되어 즉시 거부됐다. DB 에는 도달하지 않는다. */
    data object SoldOut : ReservationResult

    /** FR-7: 완충 permit 을 획득하지 못해 거부됐다. */
    data object Throttled : ReservationResult

    /** FR-6: 사용자의 1인 구매 한도를 초과했다. [limit] 은 초과 기준이 된 한도값이다. */
    data class PerUserLimitExceeded(val limit: Int) : ReservationResult
}
