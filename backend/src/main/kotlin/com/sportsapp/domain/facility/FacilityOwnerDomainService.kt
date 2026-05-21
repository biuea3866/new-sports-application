package com.sportsapp.domain.facility

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
class FacilityOwnerDomainService(
    private val facilityRepository: FacilityRepository,
) {
    fun registerForOwner(attributes: FacilityAttributes, ownerUserId: Long): Facility {
        val facility = Facility.create(attributes)
        facility.assignOwner(ownerUserId)
        return facilityRepository.save(facility)
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
