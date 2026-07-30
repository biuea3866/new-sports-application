package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.service.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetRegionTypeStatsUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(): List<RegionTypeCount> =
        facilityDomainService.aggregateRegionType()
}
