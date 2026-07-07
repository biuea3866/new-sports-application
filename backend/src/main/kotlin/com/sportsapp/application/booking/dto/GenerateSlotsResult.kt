package com.sportsapp.application.booking.dto

import com.sportsapp.domain.booking.dto.FacilitySlotGenerationOutcome

data class GenerateSlotsResult(
    val outcomes: List<FacilitySlotGenerationOutcome>,
) {
    val totalCreated: Int get() = outcomes.filter { it.succeeded }.sumOf { it.createdCount }
    val failedFacilityCount: Int get() = outcomes.count { !it.succeeded }
}
