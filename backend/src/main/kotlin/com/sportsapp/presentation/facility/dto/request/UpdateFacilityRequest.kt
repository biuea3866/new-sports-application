package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.UpdateMyFacilityCommand

data class UpdateFacilityRequest(
    val meta: Map<String, String>,
    val sido: String? = null,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = UpdateMyFacilityCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        patch = meta,
        sido = sido,
    )
}
