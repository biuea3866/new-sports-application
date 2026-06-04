package com.sportsapp.application.facility

import com.sportsapp.domain.booking.service.SlotDomainService
import com.sportsapp.domain.facility.FacilityHasActiveSlotException
import com.sportsapp.domain.facility.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class DeleteMyFacilityUseCase(
    private val slotDomainService: SlotDomainService,
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: DeleteMyFacilityCommand) {
        if (slotDomainService.hasActiveSlotsByFacilityId(command.facilityId)) {
            throw FacilityHasActiveSlotException(command.facilityId)
        }
        facilityOwnerDomainService.deleteForOwner(command.facilityId, command.ownerUserId)
    }
}
