package com.sportsapp.domain.facility

data class BulkImportResult(
    val insertedCount: Int,
    val updatedCount: Int,
    val skippedCount: Int,
) {
    val totalProcessed: Int get() = insertedCount + updatedCount + skippedCount
}
