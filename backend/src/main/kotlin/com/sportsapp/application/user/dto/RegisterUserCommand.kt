package com.sportsapp.application.user.dto

data class RegisterUserCommand(
    val email: String,
    val rawPassword: String,
)
