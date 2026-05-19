package com.sportsapp.domain.common

import java.time.Duration

/**
 * 분산 락 추상화. Redis 구현체(`RedisDistributedLock`)는 SETNX + Lua compare-and-del 기반.
 *
 * 좌석 점유 락(TICKETING-04), 예약 슬롯 락(BOOKING-03) 등에서 공통 사용한다.
 *
 * 사용 패턴:
 * ```
 * val acquired = lock.tryLock("seat:lock:42", ownerId, Duration.ofSeconds(30))
 * if (acquired) {
 *     try {
 *         // critical section
 *     } finally {
 *         lock.unlock("seat:lock:42", ownerId)
 *     }
 * }
 * ```
 */
interface DistributedLock {

    /**
     * 락 획득 시도. 동일 key 에 대해 동시 N건 호출이 발생해도 정확히 1건만 true 를 반환한다.
     *
     * @param key 락 키 (도메인 prefix + 식별자 권장: "seat:lock:42")
     * @param value 락 소유자 식별자. unlock 시 동일 값으로 검증한다.
     * @param ttl 락 자동 해제까지 남은 시간. 클라이언트 크래시 시에도 deadlock 방지.
     * @return 획득 성공 여부
     */
    fun tryLock(key: String, value: String, ttl: Duration): Boolean

    /**
     * 락 해제. 소유자가 일치할 때만 키를 삭제한다 (Lua compare-and-del).
     *
     * @param key 락 키
     * @param value 소유자 식별자. tryLock 시 사용한 값과 일치해야 한다.
     * @return 실제로 키가 삭제됐는지 여부 (소유자 불일치 시 false)
     */
    fun unlock(key: String, value: String): Boolean
}
