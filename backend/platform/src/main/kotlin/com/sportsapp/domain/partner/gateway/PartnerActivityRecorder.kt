package com.sportsapp.domain.partner.gateway

import java.time.ZonedDateTime

/**
 * 파트너 활동 감사 기록 계약. 필터가 계측한 요청 시각(calledAt)을 그대로 전달받아
 * 비동기 적재를 수행하는 infrastructure 구현체가 존재한다 (예: AsyncPartnerActivityRecorder).
 */
interface PartnerActivityRecorder {
    // LongParameterList 억제 근거(W1-DEBT-01): 감사 기록 1건의 필드를 그대로 받는 계약이다.
    // edge 는 이미 같은 필드를 PartnerApiCallActivity 값 객체로 묶어 쓰고(W1-06b), 어댑터가 이 시그니처로
    // 풀어 넘긴다 — 이 인터페이스까지 값 객체로 통일하는 것이 옳지만 platform·bootstrap 구현체와
    // 비동기 recorder 를 함께 바꿔야 해 별도 티켓으로 분리한다.
    @Suppress("LongParameterList")
    fun record(
        partnerId: Long,
        userId: Long,
        httpMethod: String,
        requestPath: String,
        statusCode: Int,
        latencyMs: Int,
        ipAddr: String?,
        userAgent: String?,
        calledAt: ZonedDateTime,
    )
}
