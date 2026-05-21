package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.facility.UpdateFacilityInfoCommand
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class UpdateFacilityUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional
    fun execute(command: UpdateFacilityCommand): FacilityResponse {
        val facility = facilityDomainService.update(
            operatorId = command.operatorId,
            facilityId = command.facilityId,
            command = UpdateFacilityInfoCommand(
                name = command.name,
                address = command.address,
                operatingHours = command.operatingHours,
                basePrice = command.basePrice,
            ),
        )
        return FacilityResponse.of(facility)
    }
}
