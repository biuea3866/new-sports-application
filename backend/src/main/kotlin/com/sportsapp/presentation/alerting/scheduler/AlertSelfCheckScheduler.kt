package com.sportsapp.presentation.alerting.scheduler

import com.sportsapp.application.alerting.usecase.SendSelfCheckUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 1시간 주기 self-check heartbeat 스케줄러 (McpAnomalyScheduler 선례, TDD.md §Detail Design).
 */
@Component
class AlertSelfCheckScheduler(
    private val sendSelfCheckUseCase: SendSelfCheckUseCase,
) {
    private val log = LoggerFactory.getLogger(AlertSelfCheckScheduler::class.java)

    @Scheduled(cron = "0 0 * * * *")
    fun runSelfCheck() {
        log.info("AlertSelfCheckScheduler: starting self-check")
        sendSelfCheckUseCase.execute()
        log.info("AlertSelfCheckScheduler: self-check completed")
    }
}
