package com.sportsapp.domain.virtualqueue.vo

/**
 * 운영자 통계(FR-11) 도메인 표현.
 *
 * [waitingCount]·[admittedCount]는 `VirtualQueueStore`에서 즉시 조회 가능한 카운트값이다.
 * [admissionRatePerSec]·[avgWaitSeconds]·[p95WaitSeconds]는 시계열 지표로, Micrometer 기반
 * Observability(`VirtualQueueMetricsBinder`, BE-10)가 소유한다 — BE-10은 Prometheus로 push되는
 * 게이지·타이머(`MeterBinder`)만 노출하고 request-time 조회 API를 갖지 않으므로, 이 시점에는
 * 실계산 없이 [METRIC_UNAVAILABLE](0.0)로 채운다. BE-10이 조회 가능한 지표 소스를 확정하면 [of]를
 * 갱신한다 — 이 VO를 소비하는 `GetQueueStatsUseCase`(BE-06)가 Micrometer를 직접 조회하지 않는다.
 */
data class QueueStats(
    val waitingCount: Long,
    val admittedCount: Long,
    val admissionRatePerSec: Double,
    val avgWaitSeconds: Double,
    val p95WaitSeconds: Double,
) {

    companion object {
        private const val METRIC_UNAVAILABLE = 0.0

        /** Store에서 즉시 조회 가능한 카운트만으로 구성한다 — 지표성 필드는 BE-10 연동 전 placeholder. */
        fun of(waitingCount: Long, admittedCount: Long): QueueStats = QueueStats(
            waitingCount = waitingCount,
            admittedCount = admittedCount,
            admissionRatePerSec = METRIC_UNAVAILABLE,
            avgWaitSeconds = METRIC_UNAVAILABLE,
            p95WaitSeconds = METRIC_UNAVAILABLE,
        )
    }
}
