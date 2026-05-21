package com.sportsapp.domain.mcp.confirm

data class ConfirmationTokenContext(
    val toolName: String,
    val userId: Long,
    val paramsHash: String,
)
