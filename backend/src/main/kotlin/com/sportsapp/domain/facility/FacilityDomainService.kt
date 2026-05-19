package com.sportsapp.domain.facility

import org.springframework.stereotype.Service

@Service
class FacilityDomainService(
    private val facilityRepository: FacilityRepository,
) {

    fun register(attributes: FacilityAttributes): Facility {
        val facility = Facility.create(attributes)
        return facilityRepository.save(facility)
    }

    fun findByGu(gu: String): List<Facility> =
        facilityRepository.findAllByGu(gu)

    fun findByGuAndType(gu: String, type: String): List<Facility> =
        facilityRepository.findAllByGuAndType(gu, type)

    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> =
        facilityRepository.findNear(lat, lng, maxDistanceMeters)
}
