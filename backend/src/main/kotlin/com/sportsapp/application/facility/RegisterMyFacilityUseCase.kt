package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: RegisterMyFacilityCommand): FacilityResponse {
        val facility = facilityOwnerDomainService.registerForOwner(command.toAttributes(), command.ownerUserId)
        return FacilityResponse.of(facility)
    }
}
