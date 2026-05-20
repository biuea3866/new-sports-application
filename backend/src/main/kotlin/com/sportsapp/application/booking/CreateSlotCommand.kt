package com.sportsapp.application.booking

import java.time.ZonedDateTime

data class CreateSlotCommand(
    val ownerId: Long,
    val facilityId: String,
    val date: ZonedDateTime,
    val timeRange: String,
    val capacity: Int,
)
