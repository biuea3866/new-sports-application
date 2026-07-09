package com.sportsapp.domain.virtualqueue.dto

/**
 * `AdmissionDomainService.runBatch`(후행 티켓)의 결과 — 한 틱의 admission 전진 결과와
 * 이탈 방출 수를 함께 담는다.
 */
data class AdmissionBatchResult(
    val admittedCount: Long,
    val evictedCount: Int,
)
