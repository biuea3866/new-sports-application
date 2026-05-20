package com.sportsapp.domain.facility

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
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

    fun list(gu: String?, type: String?, pageable: Pageable): Page<Facility> =
        facilityRepository.findAll(gu, type, pageable)
}
