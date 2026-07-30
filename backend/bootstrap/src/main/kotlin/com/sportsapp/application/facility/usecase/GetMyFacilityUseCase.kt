package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityOwnerDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class GetMyFacilityUseCase(
    private val facilityOwnerDomainService: FacilityOwnerDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String, ownerUserId: Long): Facility {
        val facility = facilityOwnerDomainService.getByIdAndOwner(facilityId, ownerUserId)
        return facility
    }
}
