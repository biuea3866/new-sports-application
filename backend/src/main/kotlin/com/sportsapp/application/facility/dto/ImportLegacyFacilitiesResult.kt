package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.dto.BulkImportResult

data class ImportLegacyFacilitiesResult(
    val result: BulkImportResult,
    val dryRun: Boolean,
) {
    val insertedCount: Int get() = result.insertedCount
    val updatedCount: Int get() = result.updatedCount
    val skippedCount: Int get() = result.skippedCount
}
