package com.sportsapp.domain.partner.gateway

import java.time.ZonedDateTime

/**
 * 파트너 활동 감사 기록 계약. 필터가 계측한 요청 시각(calledAt)을 그대로 전달받아
 * 비동기 적재를 수행하는 infrastructure 구현체가 존재한다 (예: AsyncPartnerActivityRecorder).
 */
interface PartnerActivityRecorder {
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
