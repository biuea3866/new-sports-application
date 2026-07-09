package com.sportsapp.domain.virtualqueue.gateway

import com.sportsapp.domain.virtualqueue.vo.EntryToken
import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * 입장 토큰 발급 domain gateway. HMAC 서명·검증 등 구현 상세는 infrastructure
 * (`HmacEntryTokenGateway`, 후행 티켓) 책임이다.
 */
interface EntryTokenIssuer {

    /**
     * 멱등 발급 — 동일 (target, userId) 재발급 시 기존 토큰을 반환한다(마커 `SET NX EX 300`).
     * TTL 5분. Redis 의존(멱등 마커 조회·세팅) — Redis 장애 시 예외가 전파될 수 있다.
     */
    fun issueIfAbsent(target: QueueTarget, userId: Long): EntryToken

    /**
     * fail-open 전용 — HMAC 서명만 수행하고 Redis에 접근하지 않는다(멱등 마커 생략).
     * [issueIfAbsent]의 `SET NX`가 Redis 장애로 재실패하는 것을 막기 위한 폴백 발급 경로다
     * (redis-contract §0-3). 재사용 방지 마커는 이 경로에서 애초에 생략된다.
     */
    fun mintStateless(target: QueueTarget, userId: Long): EntryToken
}
