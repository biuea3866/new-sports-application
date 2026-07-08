package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.domain.partner.audit.PartnerAuditLog
import com.sportsapp.domain.partner.audit.PartnerAuditLogDomainService
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * 파트너 요청 감사 비동기 적재. 전용 스레드풀(partnerAuditExecutor, [com.sportsapp.infrastructure.config.PartnerAsyncConfig])에서 실행돼
 * 등록 요청 P95에 감사 I/O가 끼지 않는다. 적재 실패는 요청을 실패시키지 않고 WARN 로그만 남긴다 (ADR-009).
 */
@Component
class AsyncPartnerActivityRecorder(
    private val partnerAuditLogDomainService: PartnerAuditLogDomainService,
) : PartnerActivityRecorder {

    private val logger = LoggerFactory.getLogger(AsyncPartnerActivityRecorder::class.java)

    @Async("partnerAuditExecutor")
    override fun record(
        partnerId: Long,
        userId: Long,
        httpMethod: String,
        requestPath: String,
        statusCode: Int,
        latencyMs: Int,
        ipAddr: String?,
        userAgent: String?,
        calledAt: ZonedDateTime,
    ) {
        try {
            val auditLog = PartnerAuditLog.of(
                partnerId = partnerId,
                userId = userId,
                httpMethod = httpMethod,
                requestPath = requestPath,
                targetResource = null,
                statusCode = statusCode,
                latencyMs = latencyMs,
                ipAddr = ipAddr,
                clientUserAgent = userAgent,
                calledAt = calledAt,
            )
            partnerAuditLogDomainService.record(auditLog)
        } catch (exception: Exception) {
            logger.warn(
                "partner audit log 적재 실패 — partnerId={}, requestPath={}: {}",
                partnerId,
                requestPath,
                exception.message,
            )
        }
    }
}
