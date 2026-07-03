package com.sportsapp.domain.goods.gateway

import java.time.Duration

/**
 * 한정판 회차 입장 게이트(FR-8) + 완충(FR-7) + 1인 한도(FR-6) + 멱등을 감추는 domain gateway.
 * `SeatLockStore`(domain.ticketing.gateway) 선례를 따른다.
 *
 * Redis 키·TTL·Lua 스크립트 등 구현 상세는 이 interface 의 책임이 아니다 — 구현체는
 * infrastructure 의 `DropReservationStoreImpl` (BE-04) 이 담당한다.
 */
interface DropReservationStore {

    /** 회차 개설 시 재고 카운터를 시드한다 (SET NX 의미 — 이미 존재하면 아무 것도 하지 않는다). 재실행 안전. */
    fun seedIfAbsent(dropId: Long, initialQuantity: Int, ttl: Duration)

    /**
     * 입장 게이트(FR-8) + 1인 한도(FR-6) + 멱등 마커 + 완충 permit(FR-7) 을 원자적으로 판정한다.
     *
     * 판정 순서: 멱등 마커 확인([ReservationResult.AlreadyReserved]) →
     * 1인 한도 검사(FR-6, [ReservationResult.PerUserLimitExceeded]) →
     * 재고 소진 거부(FR-8, [ReservationResult.SoldOut]) →
     * 완충 permit 획득 시도(FR-7, [ReservationResult.Throttled]) →
     * [ReservationResult.Admitted].
     *
     * [ReservationResult.Admitted] 반환 시 호출자는 완충 permit 을 보유한 상태이며,
     * 이후 반드시 [confirmSuccess] 또는 [cancel] 로 permit 을 반납해야 한다.
     */
    fun reserve(
        dropId: Long,
        userId: Long,
        quantity: Int,
        perUserLimit: Int,
        idempotencyKey: String,
    ): ReservationResult

    /** 구매 성공 확정. 차감된 재고 카운터는 유지하고 완충 permit 만 반납한다. */
    fun confirmSuccess(dropId: Long, userId: Long, idempotencyKey: String)

    /** 구매 실패. 차감된 재고 카운터·1인 카운트를 복원하고 완충 permit 을 반납한다 (언더셀 방지). */
    fun cancel(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String)

    /** 현재 잔여 수량. 카운터가 시드되지 않았으면 null. */
    fun remaining(dropId: Long): Int?
}
