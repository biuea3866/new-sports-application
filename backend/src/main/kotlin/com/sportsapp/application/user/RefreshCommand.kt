package com.sportsapp.application.user

data class RefreshCommand(
    val userId: Long,
    val refreshToken: String,
)
