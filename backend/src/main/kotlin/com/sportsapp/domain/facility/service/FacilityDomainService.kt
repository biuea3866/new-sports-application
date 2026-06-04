package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.dto.GuTypeCount
import com.sportsapp.domain.facility.dto.LegacyRow
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
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

    fun getById(id: String): Facility =
        facilityRepository.findById(id) ?: throw FacilityNotFoundException(id)

    fun aggregateGuType(): List<GuTypeCount> =
        facilityRepository.aggregateGuType()

    fun findIdsByOwnerUserId(ownerUserId: Long): List<String> =
        facilityRepository.findIdsByOwnerUserId(ownerUserId)

    fun countByOwnerUserId(ownerUserId: Long): Long =
        facilityRepository.countByOwnerUserId(ownerUserId)

    fun bulkImport(rows: List<LegacyRow>): BulkImportResult {
        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        rows.forEach { row ->
            val attributes = LegacyToFacilityMapper.map(row)
            if (attributes == null) {
                skippedCount++
                return@forEach
            }
            val facility = Facility.create(attributes)
            val existing = facilityRepository.findByCode(attributes.code)
            if (existing == null) {
                facilityRepository.save(facility)
                insertedCount++
            } else {
                facilityRepository.upsertByCode(facility)
                updatedCount++
            }
        }

        return BulkImportResult(
            insertedCount = insertedCount,
            updatedCount = updatedCount,
            skippedCount = skippedCount,
        )
    }
}
