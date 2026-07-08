package com.sportsapp.application.user.dto

data class LogoutCommand(
    val accessToken: String,
    val userId: Long,
)
