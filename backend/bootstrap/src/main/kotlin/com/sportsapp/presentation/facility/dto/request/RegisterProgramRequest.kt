package com.sportsapp.presentation.facility.dto.request

import com.sportsapp.application.facility.dto.RegisterProgramCommand
import java.math.BigDecimal

data class RegisterProgramRequest(
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val capacity: Int,
    val durationMinutes: Int,
) {
    fun toCommand(facilityId: String, ownerUserId: Long): RegisterProgramCommand = RegisterProgramCommand(
        facilityId = facilityId,
        ownerUserId = ownerUserId,
        name = name,
        description = description,
        price = price,
        capacity = capacity,
        durationMinutes = durationMinutes,
    )
}
