package com.sportsapp.presentation.facility

import com.sportsapp.application.facility.UpdateMyFacilityCommand

data class UpdateFacilityRequest(
    val meta: Map<String, String>,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = UpdateMyFacilityCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        patch = meta,
    )
}
