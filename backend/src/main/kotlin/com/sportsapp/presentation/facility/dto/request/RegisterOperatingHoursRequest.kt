package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.RegisterOperatingHoursCommand

data class RegisterOperatingHoursRequest(
    val operatingHours: List<OperatingHoursRequest>,
) {
    fun toCommand(facilityId: String, ownerUserId: Long) = RegisterOperatingHoursCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        operatingHours = operatingHours.map { it.toVo() },
    )
}
