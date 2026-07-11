package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueStats

/**
 * `GET /virtual-queues/{type}/{targetId}/stats` 응답 골격 (TDD "FE/외부 계약 — API 명세" §4 SSOT).
 */
data class QueueStatsResponse(
    val waitingCount: Long,
    val admittedCount: Long,
    val admissionRatePerSec: Double,
    val avgWaitSeconds: Double,
    val p95WaitSeconds: Double,
) {

    companion object {
        /**
         * `QueueStats` → `QueueStatsResponse` 변환 (BE-06, GetQueueStatsUseCase 전용).
         * 지표성 필드(admissionRatePerSec 등)는 `QueueStats`가 반환한 값을 그대로 통과시킨다 —
         * 여기서 Micrometer를 직접 조회하지 않는다(BE-10 Observability 소유, [QueueStats] 참조).
         */
        fun of(stats: QueueStats): QueueStatsResponse = QueueStatsResponse(
            waitingCount = stats.waitingCount,
            admittedCount = stats.admittedCount,
            admissionRatePerSec = stats.admissionRatePerSec,
            avgWaitSeconds = stats.avgWaitSeconds,
            p95WaitSeconds = stats.p95WaitSeconds,
        )
    }
}
