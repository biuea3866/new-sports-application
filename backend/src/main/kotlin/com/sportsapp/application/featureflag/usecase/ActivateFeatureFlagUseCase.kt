package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.dto.ActivateFeatureFlagCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActivateFeatureFlagUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional
    fun execute(command: ActivateFeatureFlagCommand): FeatureFlagResponse =
        FeatureFlagResponse.of(featureFlagDomainService.activate(command))
}
