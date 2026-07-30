package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.dto.LegacyRow

data class ImportLegacyFacilitiesCommand(
    val rows: List<LegacyRow>,
    val dryRun: Boolean,
)
