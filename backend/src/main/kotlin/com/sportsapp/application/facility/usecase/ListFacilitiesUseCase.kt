package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.FacilityCriteria
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityDomainService
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
    fun execute(criteria: FacilityCriteria): Page<Facility> {
        val pageable = criteria.toPageable()
        return facilityDomainService.list(criteria.effectiveGu(), criteria.effectiveType(), pageable)
    }
}
