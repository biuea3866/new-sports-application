package com.sportsapp.domain.goods.dto

/**
 * goods 만료 스위퍼(W1-11a)의 두 TTL(분) 값을 하나로 묶은 값 객체.
 *
 * `facility-booking`(W1-11c)의 `BookingExpiryTtlPolicy`와 동일한 이유로 값 객체로 묶는다 —
 * [com.sportsapp.domain.goods.service.GoodsDomainService.filterExpirable]가 인접한 동일
 * 타입(Long) `ttlMinutes`/`readyTtlMinutes`를 나란히 파라미터로 받으면 호출부가 위치 인자로
 * 뒤바뀌어 넘겨도 컴파일이 통과해 빠른/느린 TTL이 전도되는 오동작이 조용히 재발할 수 있다.
 *
 * 불변조건(`readyTtlMinutes > ttlMinutes`)은
 * [com.sportsapp.application.goods.config.GoodsOrderExpiryProperties]가 부팅 시 `require()`로
 * 강제하지만, domain은 application 레이어를 import할 수 없으므로(레이어 의존 방향) 이 값
 * 객체 자신도 같은 불변조건을 `init`에서 재검증한다 — 그래야 `GoodsOrderExpiryTtlPolicy(90, 30)`처럼
 * 두 값이 전도돼 생성돼도(named argument 없이) 이 타입 자체가 막는다.
 */
data class GoodsOrderExpiryTtlPolicy(
    val ttlMinutes: Long,
    val readyTtlMinutes: Long,
) {
    init {
        require(readyTtlMinutes > ttlMinutes) {
            "readyTtlMinutes($readyTtlMinutes)는 ttlMinutes($ttlMinutes)보다 커야 한다 — " +
                "그렇지 않으면 결제 진행 중(live)인 주문이 빠른 TTL에 의해 오만료된다"
        }
    }
}
