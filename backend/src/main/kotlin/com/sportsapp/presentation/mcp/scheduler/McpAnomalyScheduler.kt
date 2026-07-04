package com.sportsapp.presentation.mcp.scheduler

import com.sportsapp.application.mcp.usecase.DetectMcpAnomalyUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * MCP 비정상 패턴 탐지 스케줄러.
 *
 * 매 시간 정각에 모든 ACTIVE 토큰의 호출 패턴을 검사한다.
 * cold-start(생성 후 14일 미만) 토큰은 베이스라인 없이 건너뜀.
 *
 * `@ConditionalOnProperty(matchIfMissing = false)`: feature-flag wave에서 `SportsApplication`에
 * `@EnableScheduling`이 신규 추가되며, 그간 스케줄러 부재로 한 번도 발화하지 않던 이 `@Scheduled`가
 * 매시 정각 발화하게 되는 부작용을 차단한다. 프로퍼티 미설정 시 기본 비활성 — `@EnableScheduling`
 * 도입 이전과 동일한 휴면 상태를 유지한다.
 */
@Component
@ConditionalOnProperty(
    name = ["mcp.anomaly-detection.scheduler-enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class McpAnomalyScheduler(
    private val detectMcpAnomalyUseCase: DetectMcpAnomalyUseCase,
) {
    private val log = LoggerFactory.getLogger(McpAnomalyScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    fun detectAnomalies() {
        log.info("McpAnomalyScheduler: starting anomaly detection")
        detectMcpAnomalyUseCase.execute()
        log.info("McpAnomalyScheduler: anomaly detection completed")
    }
}
