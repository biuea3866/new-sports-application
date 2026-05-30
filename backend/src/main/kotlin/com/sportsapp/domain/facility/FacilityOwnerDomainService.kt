package com.sportsapp.domain.facility

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
class FacilityOwnerDomainService(
    private val facilityRepository: FacilityRepository,
    private val geocodingGateway: GeocodingGateway,
) {
    fun registerForOwner(attributes: FacilityAttributes, ownerUserId: Long): Facility {
        val facility = Facility.create(resolveCoordinates(attributes))
        facility.assignOwner(ownerUserId)
        return facilityRepository.save(facility)
    }

    // 좌표가 비어 있고(0,0) 주소가 있으면 geocoding 으로 보강합니다. 실패 시 원본을 유지합니다.
    private fun resolveCoordinates(attributes: FacilityAttributes): FacilityAttributes {
        if (attributes.lat != 0.0 || attributes.lng != 0.0) return attributes
        if (attributes.address.isBlank()) return attributes
        val coordinate = geocodingGateway.geocode(attributes.address) ?: return attributes
        return attributes.copy(lat = coordinate.lat, lng = coordinate.lng)
    }

    fun listByOwner(ownerUserId: Long, pageable: Pageable): Page<Facility> =
        facilityRepository.findByOwnerUserId(ownerUserId, pageable)

    fun getByIdAndOwner(id: String, ownerUserId: Long): Facility =
        facilityRepository.findByIdAndOwnerUserId(id, ownerUserId)
            ?: throw FacilityNotFoundException(id)

    fun updateMetaForOwner(id: String, ownerUserId: Long, patch: Map<String, String>): Facility {
        val facility = getByIdAndOwner(id, ownerUserId)
        val updated = facility.updateMeta(patch)
        return facilityRepository.save(updated)
    }

    fun deleteForOwner(id: String, ownerUserId: Long) {
        val facility = getByIdAndOwner(id, ownerUserId)
        facility.softDelete(ownerUserId)
        facilityRepository.save(facility)
    }
}
