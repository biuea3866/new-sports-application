package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.application.virtualqueue.dto.RunAdmissionBatchCommand
import com.sportsapp.domain.virtualqueue.dto.AdmissionBatchResult
import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import org.springframework.stereotype.Service

/**
 * 한 대상의 배치 admission 전진 + 이탈 방출을 1회 수행한다 (BE-07).
 *
 * `AdmissionDomainService.runBatch`에 위임만 한다 — 클러스터 단일 실행 보장(분산 락)은 호출부
 * (`AdmissionPumpScheduler`)의 책임이고, 이 UseCase는 락을 이미 획득한 상태에서만 호출된다.
 * Redis 상태 변경이라 `@Transactional`은 불요(TDD "시스템 역할 경계" — RunAdmissionBatch UseCase).
 */
@Service
class RunAdmissionBatchUseCase(
    private val admissionDomainService: AdmissionDomainService,
) {
    fun execute(command: RunAdmissionBatchCommand): AdmissionBatchResult =
        admissionDomainService.runBatch(
            target = command.target,
            batchSize = command.batchSize,
            staleSeconds = command.staleSeconds,
            maxEvictPerTick = command.maxEvictPerTick,
        )
}
