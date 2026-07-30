package com.sportsapp.presentation.alerting.scheduler

import com.sportsapp.application.alerting.usecase.PurgeExpiredAlertsUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * alerts 이력 보존 정책(기본 90일, mcp_audit_logs 선례) 정리 배치 — 일 1회 만료된 이력을
 * 하드 삭제한다. `FeatureFlagCleanupScheduler` 선례와 동일하게 도메인은 Micrometer를 모르고
 * presentation 레이어에서 직접 카운터를 증가시킨다.
 */
@Component
class AlertRetentionScheduler(
    private val purgeExpiredAlertsUseCase: PurgeExpiredAlertsUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(AlertRetentionScheduler::class.java)

    @Scheduled(cron = "\${alerting.retention.cron:0 30 3 * * *}")
    fun purgeExpiredAlerts() {
        val deletedCount = purgeExpiredAlertsUseCase.execute()
        if (deletedCount == 0L) return

        log.info("event=alert-retention-purged source=alerting deletedCount={}", deletedCount)
        meterRegistry.counter(PURGED_ALERTS_COUNTER).increment(deletedCount.toDouble())
    }

    companion object {
        private const val PURGED_ALERTS_COUNTER = "alerting_retention_purged_total"
    }
}
