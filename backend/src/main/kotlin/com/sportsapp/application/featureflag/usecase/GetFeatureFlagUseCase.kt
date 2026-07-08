package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetFeatureFlagUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(key: String): FeatureFlagResponse =
        FeatureFlagResponse.of(featureFlagDomainService.getByKey(key))
}
