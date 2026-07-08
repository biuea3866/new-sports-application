package com.sportsapp.domain.facility.service

import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.gateway.PublicSportsFacilityGateway
import com.sportsapp.domain.facility.gateway.RegionResolveGateway
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.vo.FacilityAttributes
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("!test-jpa")
class PublicFacilityImportService(
    private val publicSportsFacilityGateway: PublicSportsFacilityGateway,
    private val facilityRepository: FacilityRepository,
    private val regionResolveGateway: RegionResolveGateway,
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
                val resolved = resolveRegion(attributes)
                if (facilityRepository.findByCode(resolved.code) == null) {
                    facilityRepository.save(Facility.create(resolved))
                    inserted++
                } else {
                    facilityRepository.upsertByCode(Facility.create(resolved))
                    updated++
                }
            }
        }
        return BulkImportResult(insertedCount = inserted, updatedCount = updated, skippedCount = skipped)
    }

    // data.go.kr 응답에는 시/도 필드가 없으므로 주소 파싱만으로 해석합니다. 실패 시 UNSPECIFIED가 저장됩니다.
    private fun resolveRegion(attributes: FacilityAttributes): FacilityAttributes {
        val region = regionResolveGateway.resolve(attributes.address, attributes.sidoHint)
        return attributes.copy(region = region)
    }
}
