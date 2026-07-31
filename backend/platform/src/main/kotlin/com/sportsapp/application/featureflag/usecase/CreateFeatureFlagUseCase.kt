package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateFeatureFlagUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional
    fun execute(command: CreateFeatureFlagCommand): FeatureFlagResponse =
        FeatureFlagResponse.of(featureFlagDomainService.create(command))
}
