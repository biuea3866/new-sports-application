package com.sportsapp.application.ticketing

import java.time.ZonedDateTime

data class SelectSeatsResponse(
    val lockId: String,
    val expiresAt: ZonedDateTime,
)
