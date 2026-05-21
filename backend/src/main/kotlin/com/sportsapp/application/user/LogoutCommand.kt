package com.sportsapp.application.user

data class LogoutCommand(
    val accessToken: String,
    val userId: Long,
)
