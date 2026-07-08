package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.dto.BulkImportResult

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
