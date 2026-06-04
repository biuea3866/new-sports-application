package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.UpdateMyFacilityCommand

data class UpdateFacilityRequest(
    val meta: Map<String, String>,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = UpdateMyFacilityCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        patch = meta,
    )
}
