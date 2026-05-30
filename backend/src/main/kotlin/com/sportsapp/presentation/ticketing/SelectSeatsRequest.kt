package com.sportsapp.presentation.ticketing

import jakarta.validation.constraints.NotEmpty

data class SelectSeatsRequest(
    @field:NotEmpty(message = "seatIds must not be empty")
    val seatIds: List<Long>,
)
