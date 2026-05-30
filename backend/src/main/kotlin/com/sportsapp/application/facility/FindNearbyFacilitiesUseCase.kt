package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class FindNearbyFacilitiesUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(lat: Double, lng: Double, radiusMeters: Double): List<FacilityResponse> =
        facilityDomainService.findNear(lat, lng, radiusMeters).map(FacilityResponse::of)
}
