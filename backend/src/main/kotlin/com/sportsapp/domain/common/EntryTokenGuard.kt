package com.sportsapp.domain.common

/**
 * 구매 앞단 입장 토큰 검증 — 교차 도메인(goods·ticketing) 게이트 인터페이스.
 *
 * `DistributedLock`·`FeatureFlagEvaluator`와 동일 위치(domain.common)에 둬서, goods/ticketing이
 * `domain.virtualqueue`를 직접 import하지 않고도 검증을 수행하게 한다
 * (no-cross-context-reverse-dep — 주문 컨텍스트가 공용 게이트에 의존하는 정상 방향만 허용).
 *
 * 구현체(`HmacEntryTokenGateway`, 후행 티켓)는 HMAC 무상태 검증이라 Redis 없이 동작한다
 * (Redis 장애에도 검증 가능 — fail-open 안전성).
 */
interface EntryTokenGuard {
    fun verify(targetType: String, targetId: Long, userId: Long, rawToken: String?): Boolean
}
