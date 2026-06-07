package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.gateway.PublicSportsFacilityGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
class PublicFacilityImportService(
    private val publicSportsFacilityGateway: PublicSportsFacilityGateway,
    private val facilityRepository: FacilityRepository,
) {
    fun importAll(maxPages: Int, numOfRows: Int): BulkImportResult {
        var inserted = 0
        var updated = 0
        var skipped = 0
        for (pageNo in 1..maxPages) {
            val page = publicSportsFacilityGateway.fetchPage(pageNo, numOfRows)
            if (page.isEmpty()) break
            page.forEach { publicFacility ->
                val attributes = publicFacility.toAttributes()
                if (attributes == null) {
                    skipped++
                    return@forEach
                }
                if (facilityRepository.findByCode(attributes.code) == null) {
                    facilityRepository.save(Facility.create(attributes))
                    inserted++
                } else {
                    facilityRepository.upsertByCode(Facility.create(attributes))
                    updated++
                }
            }
        }
        return BulkImportResult(insertedCount = inserted, updatedCount = updated, skippedCount = skipped)
    }
}
