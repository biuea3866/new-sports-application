package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.ListFeatureFlagAuditLogsResponse
import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetFeatureFlagAuditLogsUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetAuditLogsCommand): ListFeatureFlagAuditLogsResponse =
        ListFeatureFlagAuditLogsResponse.of(featureFlagDomainService.getAuditLogs(command))
}
