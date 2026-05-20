package com.sportsapp.application.user

data class LoginCommand(
    val email: String,
    val rawPassword: String,
)
