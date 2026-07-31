package com.sportsapp.domain.virtualqueue.dto

/**
 * `AdmissionDomainService.runBatch`의 결과 — 한 틱의 admission 전진 결과와 이탈 방출 수를
 * 함께 담는다.
 *
 * @param deactivated seq 키가 만료된 죽은 대상이라 이번 틱의 전진을 건너뛰고 `queue:active`에서
 * 제거만 수행했는지 여부(BE-07 seq-존재 가드). true면 [admittedCount]·[evictedCount]는 실행되지
 * 않은 배치를 뜻하는 0이다.
 */
data class AdmissionBatchResult(
    val admittedCount: Long,
    val evictedCount: Int,
    val deactivated: Boolean = false,
)
