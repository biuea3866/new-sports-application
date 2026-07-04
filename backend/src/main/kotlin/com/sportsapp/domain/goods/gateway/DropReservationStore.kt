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
     * 입장 게이트(FR-8) + 1인 한도(FR-6) + 멱등 마커를 원자적으로 판정한다.
     *
     * 판정 순서: 멱등 마커 확인([ReservationResult.AlreadyReserved]) →
     * 1인 한도 검사(FR-6, [ReservationResult.PerUserLimitExceeded]) →
     * 재고 소진 거부(FR-8, [ReservationResult.SoldOut]) →
     * [ReservationResult.Admitted].
     *
     * 완충(FR-7)은 이 메서드의 책임이 아니다 — Redis 장애로 이 메서드 자체가 예외를 던져 fail-open
     * 하는 경로도 DB 쓰기 전에는 반드시 [tryAcquireThrottle]을 거쳐야 하므로, 완충 판정은 reserve
     * 성공 여부와 무관하게 호출자(DomainService)가 별도로 수행한다.
     */
    fun reserve(
        dropId: Long,
        userId: Long,
        quantity: Int,
        perUserLimit: Int,
        idempotencyKey: String,
    ): ReservationResult

    /**
     * 완충(FR-7) permit 획득을 시도한다. [reserve] 판정 결과(Admitted·AlreadyReserved·fail-open 포함)와
     * 무관하게, DB 쓰기(persistOrder) 직전에 호출한다. 인프로세스 세마포어 등 구현 상세는 구현체 책임이다.
     */
    fun tryAcquireThrottle(): Boolean

    /** [tryAcquireThrottle]로 획득한 permit을 반납한다. DB 쓰기 성공·실패와 무관하게 반드시 호출한다. */
    fun releaseThrottle()

    /** 구매 성공 확정. 현재는 별도 상태 변경이 필요 없는 훅이다(완충 permit 반납은 [releaseThrottle] 책임). */
    fun confirmSuccess(dropId: Long, userId: Long, idempotencyKey: String)

    /** 구매 실패. 차감된 재고 카운터·1인 카운트를 복원한다 (언더셀 방지). 완충 permit 반납은 별개([releaseThrottle]). */
    fun cancel(dropId: Long, userId: Long, quantity: Int, idempotencyKey: String)

    /** 현재 잔여 수량. 카운터가 시드되지 않았으면 null. */
    fun remaining(dropId: Long): Int?

    /**
     * FR-9 거부 카운터 증가(휘발성 운영 지표). Redis 장애 시 예외를 그대로 던진다 —
     * fail-open 처리는 호출자([LimitedDropDomainService])의 책임이다.
     */
    fun recordReject(dropId: Long, kind: RejectKind)

    /** FR-9 거부 집계 조회. 카운터가 기록되지 않았으면 0으로 채워 반환한다. */
    fun rejectCounts(dropId: Long): RejectCounts
}
