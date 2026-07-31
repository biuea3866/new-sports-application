package com.sportsapp.domain.virtualqueue.gateway

import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * 가상 대기열 상태(Redis)의 domain gateway. 큐 진입·순번·admission·이탈 전부를 이 인터페이스
 * 뒤로 숨긴다. Redis 키·Lua 스크립트 등 구현 상세는 infrastructure(`VirtualQueueStoreImpl`,
 * 후행 티켓) 책임이다.
 *
 * 키·Lua 계약 SSOT: `backend/docs/redis/virtual-queue-keys.md`.
 */
interface VirtualQueueStore {

    /**
     * 멱등 진입 — `enter.lua`로 원자 처리한다. 기존 진입자는 새로 채번하지 않고 기존 시퀀스를
     * 유지하며, 신규 진입만 `INCR seq`로 고정 순번을 채번한다.
     *
     * @return 부여된 고정 시퀀스(1-based). 포화(`ZCARD >= maxCapacity`)면 null.
     */
    fun enterIfAbsent(target: QueueTarget, userId: Long, maxCapacity: Int): Long?

    /** `ZRANK` — 표시용 aheadCount(동적, 이탈 시 자연 전진). 없으면 null. */
    fun rankOf(target: QueueTarget, userId: Long): Int?

    /** `ZSCORE` — admission 판정용 고정 시퀀스(제거 영향 없음). 없으면 null. */
    fun seqOf(target: QueueTarget, userId: Long): Long?

    /** `ZCARD` — 현재 대기 인원(게이지용). */
    fun waitingSize(target: QueueTarget): Long

    /** admitted_count 조회. 시드되지 않았으면 0. */
    fun admittedCount(target: QueueTarget): Long

    /** heartbeat 갱신 — `ZADD score=now(ms)`. 폴링 조회가 생존 신호를 겸한다. */
    fun touchHeartbeat(target: QueueTarget, userId: Long)

    /** waiting+heartbeat에서 `ZREM`, 토큰 마커 `DEL`. */
    fun leave(target: QueueTarget, userId: Long)

    /**
     * `admit.lua` — `admitted_count = min(admitted_count + batchSize, seq)`로 전진시킨다.
     * seenTotal의 원천은 `seq`다(`ZCARD` 아님, redis-contract §0-2 — 진입·이탈 중에도 상한이
     * 활동으로 흔들리지 않게 한다).
     *
     * @return 전진 후 admitted_count.
     */
    fun advanceAdmission(target: QueueTarget, batchSize: Int): Long

    /**
     * `evict.lua` — heartbeat가 `staleBeforeEpochMs` 이전인 member를 `maxEvictPerTick` 상한
     * 내에서 waiting+heartbeat에서 제거한다. 초과분은 다음 틱에 자연 처리된다.
     *
     * @return 방출된 member 수.
     */
    fun sweepStale(target: QueueTarget, staleBeforeEpochMs: Long, maxEvictPerTick: Int): Int

    /** `queue:active` Set 전체 조회 — pump가 활성 대상을 순회하는 인덱스(`KEYS`/`SCAN` 회피). */
    fun activeTargets(): Set<QueueTarget>

    /** `queue:active`에 대상을 등록한다(신규 진입 시 호출). */
    fun registerActive(target: QueueTarget)

    /**
     * `EXISTS` — seq 키(고정 시퀀스 채번 + seenTotal 상한 원천) 생존 여부.
     *
     * pump(BE-07)가 `advanceAdmission`을 호출하기 전에 확인해야 하는 방어적 가드다 — seq가
     * 만료된 뒤 `admit.lua`가 실행되면 `GET seq or '0'` → 0 → `admitted_count = min(count+batch, 0)
     * = 0`으로 고수위가 역행/붕괴한다(폴링이 지속되는 한 `touchHeartbeat`가 seq TTL을 함께 슬라이딩
     * 갱신하므로 생존하지만, 폴링·이탈 모두 끊긴 죽은 대상은 seq가 만료된다).
     */
    fun seqExists(target: QueueTarget): Boolean

    /** `queue:active`에서 대상을 제거한다(`SREM`) — seq가 만료된 죽은 대상을 pump가 정리할 때 호출한다. */
    fun deactivate(target: QueueTarget)
}
