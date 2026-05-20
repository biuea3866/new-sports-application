package com.sportsapp.application.user

data class RevokeRoleCommand(
    val adminId: Long,
    val userId: Long,
    val roleName: String,
)
