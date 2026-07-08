package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.UpdateMyFacilityCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class UpdateMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: UpdateMyFacilityCommand): Facility {
        val facility = facilityOwnerDomainService.updateMetaForOwner(
            id = command.facilityId,
            ownerUserId = command.ownerUserId,
            patch = command.patch,
            sido = command.sido,
        )
        return facility
    }
}
