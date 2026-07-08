package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.AddHolidayCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class AddHolidayUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional
    fun execute(command: AddHolidayCommand): Facility =
        facilityOwnerDomainService.addHoliday(
            facilityId = command.facilityId,
            ownerUserId = command.ownerUserId,
            date = command.date,
        )
}
