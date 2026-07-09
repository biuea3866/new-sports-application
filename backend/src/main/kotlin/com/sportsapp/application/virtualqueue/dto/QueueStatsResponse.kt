package com.sportsapp.application.virtualqueue.dto

/**
 * `GET /virtual-queues/{type}/{targetId}/stats` 응답 골격 (TDD "FE/외부 계약 — API 명세" §4 SSOT).
 */
data class QueueStatsResponse(
    val waitingCount: Long,
    val admittedCount: Long,
    val admissionRatePerSec: Double,
    val avgWaitSeconds: Double,
    val p95WaitSeconds: Double,
)
