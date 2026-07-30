package com.sportsapp.application.virtualqueue.dto

import com.sportsapp.domain.virtualqueue.vo.QueueTarget

/**
 * `RunAdmissionBatchUseCase` 실행 파라미터 — `AdmissionPumpScheduler`(BE-07)가 분산 락 획득에
 * 성공한 대상마다 1건씩 만들어 전달한다.
 */
data class RunAdmissionBatchCommand(
    val target: QueueTarget,
    val batchSize: Int,
    val staleSeconds: Long,
    val maxEvictPerTick: Int,
)
