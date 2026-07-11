package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import org.springframework.stereotype.Service

/**
 * Admission Pump 운영 킬 스위치 판정 — `AdmissionPumpScheduler`가 매 틱 호출해 배치 실행 여부를
 * 결정한다.
 *
 * 킬 스위치는 부팅 시 고정되는 `@Value`가 아니라 `AdmissionDomainService.isPumpEnabled`를 거쳐
 * `FeatureFlagEvaluator`로 **매 틱 런타임 조회**한다(no-conditional-on-property) — 관리 플래그를
 * OFF로 활성화하면 재기동 없이 다음 틱부터 즉시 반영된다.
 */
@Service
class IsAdmissionPumpEnabledUseCase(
    private val admissionDomainService: AdmissionDomainService,
) {
    fun execute(): Boolean = admissionDomainService.isPumpEnabled()
}
