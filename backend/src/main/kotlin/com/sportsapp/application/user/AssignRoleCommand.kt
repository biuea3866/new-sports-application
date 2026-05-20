package com.sportsapp.application.user

data class AssignRoleCommand(
    val adminId: Long,
    val userId: Long,
    val roleName: String,
)
