package com.sportsapp.presentation.user.dto.request

import com.sportsapp.application.user.dto.RefreshCommand
import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
) {
    fun toCommand(): RefreshCommand = RefreshCommand(refreshToken = refreshToken)
}
