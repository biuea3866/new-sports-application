package com.sportsapp.application.mcp.dto

data class RevokeMcpTokenCommand(
    val tokenId: Long,
    val requesterId: Long,
)
