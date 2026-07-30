package com.sportsapp.application.ticketing.dto

import java.time.ZonedDateTime

data class SelectSeatsResponse(
    val lockId: String,
    val expiresAt: ZonedDateTime,
)
