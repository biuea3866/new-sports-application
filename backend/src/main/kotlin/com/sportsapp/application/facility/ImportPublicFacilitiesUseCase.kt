package com.sportsapp.application.facility

import com.sportsapp.domain.facility.PublicFacilityImportService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ImportPublicFacilitiesUseCase(
    private val publicFacilityImportService: PublicFacilityImportService,
) {
    @Transactional
    fun execute(maxPages: Int, numOfRows: Int): ImportPublicFacilitiesResponse {
        val result = publicFacilityImportService.importAll(maxPages, numOfRows)
        return ImportPublicFacilitiesResponse.of(result)
    }
}
