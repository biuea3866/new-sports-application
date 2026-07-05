package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.dto.GuTypeCount
import com.sportsapp.domain.facility.dto.LegacyRow
import com.sportsapp.domain.facility.dto.RegionTypeCount
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
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
    private val regionResolveGateway: RegionResolveGateway,
) {

    fun register(attributes: FacilityAttributes): Facility {
        val facility = Facility.create(resolveRegion(attributes))
        return facilityRepository.save(facility)
    }

    fun findByGu(gu: String): List<Facility> =
        facilityRepository.findAllByGu(gu)

    fun findByGuAndType(gu: String, type: String): List<Facility> =
        facilityRepository.findAllByGuAndType(gu, type)

    fun findNear(lat: Double, lng: Double, maxDistanceMeters: Double): List<Facility> =
        facilityRepository.findNear(lat, lng, maxDistanceMeters)

    fun list(sidoCode: String?, sigunguCode: String?, gu: String?, type: String?, pageable: Pageable): Page<Facility> =
        facilityRepository.findAll(sidoCode, sigunguCode, gu, type, pageable)

    fun getById(id: String): Facility =
        facilityRepository.findById(id) ?: throw FacilityNotFoundException(id)

    fun aggregateGuType(): List<GuTypeCount> =
        facilityRepository.aggregateGuType()

    fun aggregateRegionType(): List<RegionTypeCount> =
        facilityRepository.aggregateRegionType()

    fun findIdsByOwnerUserId(ownerUserId: Long): List<String> =
        facilityRepository.findIdsByOwnerUserId(ownerUserId)

    fun countByOwnerUserId(ownerUserId: Long): Long =
        facilityRepository.countByOwnerUserId(ownerUserId)

    fun bulkImport(rows: List<LegacyRow>): BulkImportResult {
        var insertedCount = 0
        var updatedCount = 0
        var skippedCount = 0

        rows.forEach { row ->
            val mapped = LegacyToFacilityMapper.map(row)
            if (mapped == null) {
                skippedCount++
                return@forEach
            }
            val attributes = resolveRegion(mapped)
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

    private fun resolveRegion(attributes: FacilityAttributes): FacilityAttributes {
        val region = regionResolveGateway.resolve(attributes.address, attributes.sidoHint)
        return attributes.copy(region = region)
    }
}
