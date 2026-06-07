package com.sportsapp.application.user.dto

data class AssignRoleCommand(
    val adminId: Long,
    val userId: Long,
    val roleName: String,
)
