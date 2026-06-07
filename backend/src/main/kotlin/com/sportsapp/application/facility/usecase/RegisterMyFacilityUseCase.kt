package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RegisterMyFacilityCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: RegisterMyFacilityCommand): Facility {
        val facility = facilityOwnerDomainService.registerForOwner(command.toAttributes(), command.ownerUserId)
        return facility
    }
}
