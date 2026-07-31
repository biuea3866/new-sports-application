package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.gateway.PartnerApiKeyUsageRecorder
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

/**
 * Partner API Key 사용 시각(lastUsedAt) 비동기 갱신. 감사 적재와 동일한 전용 스레드풀
 * (partnerAuditExecutor, [com.sportsapp.infrastructure.config.PartnerAsyncConfig])에서 실행돼
 * 인증 요청 critical path에 쓰기 지연이 끼지 않는다. 갱신 실패는 요청을 실패시키지 않고 WARN 로그만 남긴다.
 */
@Component
class AsyncPartnerApiKeyUsageRecorder(
    private val partnerDomainService: PartnerDomainService,
) : PartnerApiKeyUsageRecorder {

    private val logger = LoggerFactory.getLogger(AsyncPartnerApiKeyUsageRecorder::class.java)

    @Async("partnerAuditExecutor")
    override fun recordUsage(keyId: Long) {
        try {
            partnerDomainService.recordKeyUsage(keyId)
        } catch (exception: Exception) {
            logger.warn(
                "partner api key lastUsedAt 갱신 실패 — keyId={}: {}",
                keyId,
                exception.message,
            )
        }
    }
}
