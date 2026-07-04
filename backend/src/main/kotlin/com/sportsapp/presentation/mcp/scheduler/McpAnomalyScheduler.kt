package com.sportsapp.presentation.mcp.scheduler

import com.sportsapp.application.mcp.usecase.DetectMcpAnomalyUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * MCP 비정상 패턴 탐지 스케줄러.
 *
 * 매 시간 정각에 모든 ACTIVE 토큰의 호출 패턴을 검사한다.
 * cold-start(생성 후 14일 미만) 토큰은 베이스라인 없이 건너뜀.
 */
@Component
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
