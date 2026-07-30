package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.stereotype.Service

/**
 * 활성 대기열 대상(`queue:active`) 목록을 조회한다 — `AdmissionPumpScheduler`가 매 틱 순회할
 * 대상을 얻기 위해 호출한다.
 *
 * 스케줄러(presentation)가 domain interface(`VirtualQueueStore`)를 직접 참조하지 않도록
 * `AdmissionDomainService`에 위임만 한다(TDD "시스템 역할 경계" — `AdmissionPumpScheduler`는
 * `RunAdmissionBatchUseCase`·`DistributedLock`만 의존).
 */
@Service
class ListActiveQueueTargetsUseCase(
    private val admissionDomainService: AdmissionDomainService,
) {
    fun execute(): Set<QueueTarget> = admissionDomainService.activeTargets()
}
