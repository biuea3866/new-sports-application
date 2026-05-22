package com.sportsapp.domain.mcp

import com.sportsapp.domain.common.DomainEventPublisher
import org.springframework.stereotype.Service
import java.time.ZonedDateTime

@Service
class McpAnomalyDomainService(
    private val mcpTokenRepository: McpTokenRepository,
    private val mcpAuditLogCustomRepository: McpAuditLogCustomRepository,
    private val domainEventPublisher: DomainEventPublisher,
    private val anomalyDetector: McpAnomalyDetector,
) {
    companion object {
        const val BASELINE_WINDOW_DAYS = 7L
    }

    /**
     * 모든 ACTIVE 토큰에 대해 비정상 패턴 탐지를 수행한다.
     */
    fun detectAll() {
        val activeTokenIds = mcpTokenRepository.findAllActiveIds()
        activeTokenIds.forEach { tokenId -> detectForToken(tokenId) }
    }

    /**
     * 단일 토큰에 대한 비정상 패턴 탐지.
     * - cold-start(생성 후 14일 미만) 토큰은 건너뜀
     * - 베이스라인(직전 1시간 이전 7일치) 대비 2배 이상이면 [McpAnomalyDetectedEvent] 발행
     */
    fun detectForToken(tokenId: Long) {
        val token = mcpTokenRepository.findById(tokenId) ?: return
        if (anomalyDetector.isColdStart(token.createdAt)) return

        val now = ZonedDateTime.now()
        val baselineFrom = now.minusDays(BASELINE_WINDOW_DAYS)
        val currentHourFrom = now.minusHours(1)

        val baselineCounts = mcpAuditLogCustomRepository.findHourlyCallCountsForBaseline(
            tokenId = tokenId,
            from = baselineFrom,
            to = currentHourFrom,
        )
        val baselineAverage = anomalyDetector.computeBaselineAverage(baselineCounts.map { it.callCount })
        val currentHourCount = mcpAuditLogCustomRepository.findCurrentHourCallCount(
            tokenId = tokenId,
            from = currentHourFrom,
        )

        publishIfAnomaly(tokenId, token.userId, baselineAverage, currentHourCount)
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
