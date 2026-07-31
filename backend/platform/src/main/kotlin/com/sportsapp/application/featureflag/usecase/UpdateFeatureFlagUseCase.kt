package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.dto.UpdateFeatureFlagCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateFeatureFlagUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional
    fun execute(command: UpdateFeatureFlagCommand): FeatureFlagResponse =
        FeatureFlagResponse.of(featureFlagDomainService.update(command))
}
