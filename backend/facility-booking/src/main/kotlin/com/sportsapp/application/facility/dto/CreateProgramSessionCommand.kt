package com.sportsapp.application.facility.dto

import java.time.ZonedDateTime

data class CreateProgramSessionCommand(
    val requesterId: Long,
    val programId: Long,
    val date: ZonedDateTime,
    val timeRange: String,
)
