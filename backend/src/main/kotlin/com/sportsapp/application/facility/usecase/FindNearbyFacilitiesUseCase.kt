package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.service.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class FindNearbyFacilitiesUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(lat: Double, lng: Double, radiusMeters: Double): List<Facility> =
        facilityDomainService.findNear(lat, lng, radiusMeters)
}
