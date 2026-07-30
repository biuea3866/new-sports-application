package com.sportsapp.domain.partner.gateway

/**
 * Partner API Key 사용 시각(lastUsedAt) 기록 계약. 인증 성공 직후 필터가 호출하며,
 * 구현체는 비동기로 적재해 요청 critical path에 DB 쓰기 지연이 끼지 않게 한다
 * (예: AsyncPartnerApiKeyUsageRecorder).
 */
interface PartnerApiKeyUsageRecorder {
    fun recordUsage(keyId: Long)
}
