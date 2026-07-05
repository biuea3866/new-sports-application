package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.DeleteMyFacilityCommand
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class DeleteMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: DeleteMyFacilityCommand) {
        facilityOwnerDomainService.deleteForOwner(command.facilityId, command.ownerUserId)
    }
}
