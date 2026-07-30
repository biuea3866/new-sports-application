package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.service.PublicFacilityImportService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ImportPublicFacilitiesUseCase(
    private val publicFacilityImportService: PublicFacilityImportService,
) {
    @Transactional
    fun execute(maxPages: Int, numOfRows: Int): BulkImportResult {
        val result = publicFacilityImportService.importAll(maxPages, numOfRows)
        return result
    }
}
