package com.sportsapp.domain.virtualqueue.vo

import java.time.ZonedDateTime

/**
 * 입장 토큰 VO. HMAC 서명 원문([raw])과 만료 시각을 보유한다.
 *
 * 시간 판단은 캡슐화 메서드 내부에서 해결한다(no-time-parameter) — 호출부가 `now`를 인자로
 * 넘기지 않는다.
 */
data class EntryToken(
    val raw: String,
    val expiresAt: ZonedDateTime,
) {
    fun isExpired(): Boolean = expiresAt.isBefore(ZonedDateTime.now())
}
