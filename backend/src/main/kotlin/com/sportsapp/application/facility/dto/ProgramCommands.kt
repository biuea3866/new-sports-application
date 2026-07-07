package com.sportsapp.application.facility.dto

import java.math.BigDecimal

data class RegisterProgramCommand(
    val facilityId: String,
    val ownerUserId: Long,
    val name: String,
    val description: String?,
    val price: BigDecimal,
    val capacity: Int,
    val durationMinutes: Int,
)
