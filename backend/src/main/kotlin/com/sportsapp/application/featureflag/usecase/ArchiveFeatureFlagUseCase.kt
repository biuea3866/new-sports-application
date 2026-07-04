package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArchiveFeatureFlagUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional
    fun execute(command: ArchiveFeatureFlagCommand): FeatureFlagResponse =
        FeatureFlagResponse.of(featureFlagDomainService.archive(command))
}
