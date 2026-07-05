package com.sportsapp.presentation.featureflag.scheduler

import com.sportsapp.application.featureflag.usecase.DetectStaleFeatureFlagsUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 정리 후보(FR-14, P2) 탐지 스케줄러 — 일 1회 90일 무변경 ACTIVE RELEASE 플래그를 탐지해
 * 옵저버빌리티 대시보드(로그·카운터)로 통지한다. 결과 미추적(fire-and-forget) — 상태 변경 없이
 * 통지만 한다.
 *
 * `LimitedDropOversoldEventWorker` 선례와 동일하게 도메인은 Micrometer를 알지 못하고,
 * presentation 레이어에서 직접 카운터를 증가시킨다.
 */
@Component
class FeatureFlagCleanupScheduler(
    private val detectStaleFeatureFlagsUseCase: DetectStaleFeatureFlagsUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(FeatureFlagCleanupScheduler::class.java)

    @Scheduled(cron = "\${feature-flag.cleanup.cron:0 0 3 * * *}")
    fun detectStaleCandidates() {
        val staleCandidates = detectStaleFeatureFlagsUseCase.execute()
        if (staleCandidates.isEmpty()) return

        staleCandidates.forEach { candidate ->
            log.info(
                "event=feature-flag-stale-candidate-detected source=feature-flag flagKey={} updatedAt={}",
                candidate.key,
                candidate.updatedAt,
            )
        }
        meterRegistry.counter(STALE_CANDIDATES_COUNTER).increment(staleCandidates.size.toDouble())
    }

    companion object {
        private const val STALE_CANDIDATES_COUNTER = "feature_flag_stale_candidates_total"
    }
}
