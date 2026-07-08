package com.sportsapp.application.user.dto

data class RevokeRoleCommand(
    val adminId: Long,
    val userId: Long,
    val roleName: String,
)
