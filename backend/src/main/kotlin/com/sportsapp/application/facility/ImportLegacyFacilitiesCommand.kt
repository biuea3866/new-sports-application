package com.sportsapp.application.facility

import com.sportsapp.domain.facility.LegacyRow

data class ImportLegacyFacilitiesCommand(
    val rows: List<LegacyRow>,
    val dryRun: Boolean,
)
