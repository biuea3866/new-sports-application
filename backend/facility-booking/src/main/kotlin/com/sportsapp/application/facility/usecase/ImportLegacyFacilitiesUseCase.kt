package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.ImportLegacyFacilitiesCommand
import com.sportsapp.application.facility.dto.ImportLegacyFacilitiesResult
import com.sportsapp.domain.facility.dto.BulkImportResult
import com.sportsapp.domain.facility.service.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ImportLegacyFacilitiesUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional
    fun execute(command: ImportLegacyFacilitiesCommand): ImportLegacyFacilitiesResult {
        if (command.dryRun) {
            return ImportLegacyFacilitiesResult(result = BulkImportResult(insertedCount = command.rows.size, updatedCount = 0, skippedCount = 0), dryRun = true)
        }
        val result = facilityDomainService.bulkImport(command.rows)
        return ImportLegacyFacilitiesResult(result = result, dryRun = false)
    }
}
