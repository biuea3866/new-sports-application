package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 정리 후보 탐지(FR-14, P2) — 90일 무변경 ACTIVE RELEASE 플래그를 조회한다. presentation의 일 1회
 * 스케줄러가 위임 호출한다.
 */
@Service
class DetectStaleFeatureFlagsUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(): List<FeatureFlagResponse> =
        featureFlagDomainService.findStaleReleaseFlags().map { FeatureFlagResponse.of(it) }
}
