package com.sportsapp.application.facility

import com.sportsapp.domain.facility.BulkImportResult

data class ImportPublicFacilitiesResponse(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
) {
    companion object {
        fun of(result: BulkImportResult): ImportPublicFacilitiesResponse = ImportPublicFacilitiesResponse(
            insertedCount = result.insertedCount,
            updatedCount = result.updatedCount,
            skippedCount = result.skippedCount,
        )
    }
}
