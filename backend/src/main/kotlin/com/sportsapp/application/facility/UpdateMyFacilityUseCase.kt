package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class UpdateMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: UpdateMyFacilityCommand): FacilityResponse {
        val facility = facilityOwnerDomainService.updateMetaForOwner(
            id = command.facilityId,
            ownerUserId = command.ownerUserId,
            patch = command.patch,
        )
        return FacilityResponse.of(facility)
    }
}
