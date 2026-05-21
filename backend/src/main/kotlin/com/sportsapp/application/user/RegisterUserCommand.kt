package com.sportsapp.application.user

data class RegisterUserCommand(
    val email: String,
    val rawPassword: String,
)
