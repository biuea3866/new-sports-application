package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.dto.ListFeatureFlagsCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListFeatureFlagsUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListFeatureFlagsCommand): List<FeatureFlagResponse> =
        featureFlagDomainService.findAll(command.status, command.type).map { FeatureFlagResponse.of(it) }
}
