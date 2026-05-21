package com.sportsapp.application.facility

import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterMyFacilityUseCase(
    private val facilityDomainService: FacilityDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: RegisterMyFacilityCommand): MyFacilityResponse {
        val authUserId = command.authUserId
        val facility = facilityDomainService.registerForOwner(command.toAttributes(), authUserId)
        return MyFacilityResponse.of(facility)
    }
}
