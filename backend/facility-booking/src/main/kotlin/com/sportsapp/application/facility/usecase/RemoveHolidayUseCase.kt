package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.RemoveHolidayCommand
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityScheduleOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class RemoveHolidayUseCase(
    private val facilityScheduleOwnerDomainService: FacilityScheduleOwnerDomainService,
) {
    @Transactional
    fun execute(command: RemoveHolidayCommand): Facility =
        facilityScheduleOwnerDomainService.removeHoliday(
            facilityId = command.facilityId,
            ownerUserId = command.ownerUserId,
            date = command.date,
        )
}
