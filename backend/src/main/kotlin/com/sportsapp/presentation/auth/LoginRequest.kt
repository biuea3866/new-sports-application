package com.sportsapp.presentation.auth

import com.sportsapp.application.user.LoginCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val password: String,
) {
    fun toCommand(): LoginCommand = LoginCommand(email = email, rawPassword = password)
}
