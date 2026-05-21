package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String, ownerUserId: Long): FacilityResponse {
        val facility = facilityOwnerDomainService.getByIdAndOwner(facilityId, ownerUserId)
        return FacilityResponse.of(facility)
    }
}
