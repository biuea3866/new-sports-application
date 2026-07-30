package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RegisterOperatingHoursCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RegisterOperatingHoursUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: RegisterOperatingHoursCommand): Facility =
        facilityOwnerDomainService.registerOperatingHours(
            facilityId = command.facilityId,
            ownerUserId = command.ownerUserId,
            operatingHours = command.operatingHours,
        )
}
