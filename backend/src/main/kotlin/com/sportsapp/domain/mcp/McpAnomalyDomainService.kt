package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.DomainEventPublisher
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class McpAnomalyDomainService(
    private val mcpTokenRepository: McpTokenRepository,
    private val mcpAuditLogCustomRepository: McpAuditLogCustomRepository,
    private val domainEventPublisher: DomainEventPublisher,
) {
    private val anomalyDetector = McpAnomalyDetector()
    /**
     * 모든 ACTIVE 토큰에 대해 비정상 패턴 탐지를 수행한다.
     */
    fun detectAll() {
        val activeTokens = mcpTokenRepository.findAllActive()
        activeTokens.forEach { token -> detectForToken(token) }
    }

    /**
     * 단일 토큰에 대한 비정상 패턴 탐지.
     * - cold-start(생성 후 14일 미만) 토큰은 건너뜀
     * - 베이스라인(직전 1시간 이전 7일치) 일평균 대비 2배 이상이면 [McpAnomalyDetectedEvent] 발행
     */
    fun detectForToken(tokenId: Long) {
        val token = mcpTokenRepository.findById(tokenId) ?: return
        detectForToken(token)
    }

    private fun detectForToken(token: McpToken) {
        if (anomalyDetector.isColdStart(token.createdAt)) return
        val now = ZonedDateTime.now()
        val currentHourFrom = now.minusHours(1)
        val baselineAverage = fetchBaselineAverage(token.id, now, currentHourFrom)
        val currentHourCount = mcpAuditLogCustomRepository.findCurrentHourCallCount(
            tokenId = token.id,
            from = currentHourFrom,
        )
        publishIfAnomaly(token.id, token.userId, baselineAverage, currentHourCount)
    }

    private fun fetchBaselineAverage(
        tokenId: Long,
        now: ZonedDateTime,
        currentHourFrom: ZonedDateTime,
    ): Double {
        val dailyCounts = mcpAuditLogCustomRepository.findDailyCallCountsForBaseline(
            tokenId = tokenId,
            from = now.minusDays(McpAnomalyDetector.BASELINE_WINDOW_DAYS),
            to = currentHourFrom,
        )
        return anomalyDetector.computeBaselineAverage(dailyCounts.map { it.callCount })
    }

    private fun publishIfAnomaly(
        tokenId: Long,
        userId: Long,
        baselineAverage: Double,
        currentHourCount: Long,
    ) {
        if (!anomalyDetector.isAnomaly(baselineAverage, currentHourCount)) return
        domainEventPublisher.publish(
            McpAnomalyDetectedEvent(
                tokenId = tokenId,
                userId = userId,
                currentHourCount = currentHourCount,
                baselineAverage = baselineAverage,
            )
        )
    }
}
