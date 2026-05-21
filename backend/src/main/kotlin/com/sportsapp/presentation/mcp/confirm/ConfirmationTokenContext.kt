package com.sportsapp.presentation.mcp.confirm

data class ConfirmationTokenContext(
    val toolName: String,
    val userId: Long,
    val paramsHash: String,
)
