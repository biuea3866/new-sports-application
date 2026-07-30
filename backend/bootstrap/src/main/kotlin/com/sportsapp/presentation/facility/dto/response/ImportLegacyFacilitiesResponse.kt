package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.dto.BulkImportResult

data class ImportLegacyFacilitiesResponse(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
    val dryRun: Boolean,
) {
    companion object {
        fun of(result: BulkImportResult, dryRun: Boolean) = ImportLegacyFacilitiesResponse(
            insertedCount = result.insertedCount,
            updatedCount = result.updatedCount,
            skippedCount = result.skippedCount,
            dryRun = dryRun,
        )

        fun dryRunPreview(rows: Int) = ImportLegacyFacilitiesResponse(
            insertedCount = rows,
            updatedCount = 0,
            skippedCount = 0,
            dryRun = true,
        )
    }
}
