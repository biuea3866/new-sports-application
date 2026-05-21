package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.ImportLegacyFacilitiesCommand
import com.sportsapp.domain.facility.LegacyRow

data class LegacyRowRequest(
    val legacyId: String,
    val name: String,
    val gu: String,
    val type: String,
    val address: String,
    val ycode: String,
    val xcode: String,
    val parking: Boolean,
    val tel: String,
    val homePage: String,
    val eduYn: Boolean,
    val extraFields: Map<String, String> = emptyMap(),
) {
    fun toLegacyRow() = LegacyRow(
        legacyId = legacyId,
        name = name,
        gu = gu,
        type = type,
        address = address,
        ycode = ycode,
        xcode = xcode,
        parking = parking,
        tel = tel,
        homePage = homePage,
        eduYn = eduYn,
        extraFields = extraFields,
    )
}

data class ImportLegacyFacilitiesRequest(
    val rows: List<LegacyRowRequest>,
    val dryRun: Boolean = false,
) {
    fun toCommand() = ImportLegacyFacilitiesCommand(
        rows = rows.map { it.toLegacyRow() },
        dryRun = dryRun,
    )
}
