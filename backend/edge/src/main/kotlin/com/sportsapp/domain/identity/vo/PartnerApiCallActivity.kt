package com.sportsapp.domain.identity.vo

import java.time.ZonedDateTime

/**
 * 파트너 API 호출 감사 기록 1건 (W1-06b).
 *
 * 오늘 `PartnerApiKeyAuthenticationFilter` 가 `PartnerActivityRecorder.record(...)` 에 8개 인자를
 * 나열해 넘기던 것을 값 객체로 묶는다 — edge 가 platform 의 recorder 타입을 알 수 없으므로
 * edge 소유 값 객체로 게이트웨이 경계를 넘기고, 어댑터가 platform recorder 시그니처로 풀어 넘긴다.
 */
data class PartnerApiCallActivity(
    val partnerId: Long,
    val userId: Long,
    val httpMethod: String,
    val requestPath: String,
    val statusCode: Int,
    val latencyMs: Int,
    val ipAddr: String?,
    val userAgent: String?,
    val calledAt: ZonedDateTime,
)
