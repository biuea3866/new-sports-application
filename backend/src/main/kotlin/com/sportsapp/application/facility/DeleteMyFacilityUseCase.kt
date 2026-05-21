package com.sportsapp.application.facility

import com.sportsapp.domain.booking.SlotDomainService
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.FacilityHasActiveSlotException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class DeleteMyFacilityUseCase(
    private val facilityDomainService: FacilityDomainService,
    private val slotDomainService: SlotDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    @Transactional
    fun execute(command: DeleteMyFacilityCommand) {
        if (slotDomainService.existsActiveByFacilityId(command.facilityId)) {
            throw FacilityHasActiveSlotException(command.facilityId)
        }
        facilityDomainService.deleteForOwner(command.facilityId, command.authUserId)
    }
}
