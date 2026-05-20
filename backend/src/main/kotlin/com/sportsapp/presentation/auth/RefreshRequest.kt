package com.sportsapp.presentation.auth

import com.sportsapp.application.user.RefreshCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

data class RefreshRequest(
    @field:Positive val userId: Long,
    @field:NotBlank val refreshToken: String,
) {
    fun toCommand(): RefreshCommand = RefreshCommand(userId = userId, refreshToken = refreshToken)
}
