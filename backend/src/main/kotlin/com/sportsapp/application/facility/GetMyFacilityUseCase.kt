package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetMyFacilityUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String, authUserId: Long): MyFacilityResponse {
        val facility = facilityDomainService.getByIdAndOwnerUserId(facilityId, authUserId)
        return MyFacilityResponse.of(facility)
    }
}
