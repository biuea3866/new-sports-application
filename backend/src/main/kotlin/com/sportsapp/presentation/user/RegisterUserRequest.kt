package com.sportsapp.presentation.user

import com.sportsapp.application.user.RegisterUserCommand
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterUserRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Size(min = 8) val password: String,
) {
    fun toCommand(): RegisterUserCommand = RegisterUserCommand(
        email = email,
        rawPassword = password,
    )
}
