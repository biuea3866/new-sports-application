package com.sportsapp.domain.mcp.dto

data class ConfirmationTokenContext(
    val toolName: String,
    val userId: Long,
    val paramsHash: String,
)
