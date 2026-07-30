package com.sportsapp.presentation.facility.dto.response

import com.sportsapp.domain.facility.dto.BackfillResult

data class BackfillFacilityRegionResponse(
    val updated: Int,
    val unspecified: Int,
) {
    companion object {
        fun of(result: BackfillResult): BackfillFacilityRegionResponse = BackfillFacilityRegionResponse(
            updated = result.updated,
            unspecified = result.unspecified,
        )
    }
}
