package com.sportsapp.presentation.auth

import com.sportsapp.application.user.RefreshCommand
import jakarta.validation.constraints.NotBlank

data class RefreshRequest(
    @field:NotBlank val refreshToken: String,
) {
    fun toCommand(): RefreshCommand = RefreshCommand(refreshToken = refreshToken)
}
