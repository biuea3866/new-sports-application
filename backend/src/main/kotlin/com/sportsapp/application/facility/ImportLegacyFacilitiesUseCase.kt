package com.sportsapp.application.facility

import com.sportsapp.domain.facility.FacilityDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Profile("!test-jpa")
class ImportLegacyFacilitiesUseCase(
    private val facilityDomainService: FacilityDomainService,
) {
    @Transactional
    fun execute(command: ImportLegacyFacilitiesCommand): ImportLegacyFacilitiesResponse {
        if (command.dryRun) {
            return ImportLegacyFacilitiesResponse.dryRunPreview(command.rows.size)
        }
        val result = facilityDomainService.bulkImport(command.rows)
        return ImportLegacyFacilitiesResponse.of(result, dryRun = false)
    }
}
