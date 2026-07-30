package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.vo.OperatingHours

data class RegisterOperatingHoursCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val operatingHours: List<OperatingHours>,
)
