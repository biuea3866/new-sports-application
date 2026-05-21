package com.sportsapp.application.facility

import java.time.ZonedDateTime

data class GetFacilityStatsCommand(
    val operatorId: Long,
    val facilityId: String?,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
