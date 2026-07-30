package com.sportsapp.application.user.dto

data class LoginCommand(
    val email: String,
    val rawPassword: String,
)
