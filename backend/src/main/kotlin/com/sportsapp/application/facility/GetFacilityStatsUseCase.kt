package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetFacilityStatsUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetFacilityStatsCommand): List<FacilityStatsResponse> =
        facilityDomainService.aggregateStats(command.operatorId, command.facilityId, command.from, command.to)
            .map { FacilityStatsResponse.of(it) }
}
