package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ListFacilitiesUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: FacilityCriteria): Page<FacilityResponse> {
        val pageable = criteria.toPageable()
        return facilityDomainService.list(criteria.effectiveGu(), criteria.effectiveType(), pageable)
            .map { FacilityResponse.of(it) }
    }
}
