package com.sportsapp.application.user

data class GetOperatorProfileCommand(
    val requestUserId: Long,
    val targetUserId: Long,
)
