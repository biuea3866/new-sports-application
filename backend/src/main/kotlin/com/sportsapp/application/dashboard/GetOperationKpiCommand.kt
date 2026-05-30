package com.sportsapp.application.dashboard

import java.time.ZonedDateTime

data class GetOperationKpiCommand(
    val ownerUserId: Long,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
