package com.sportsapp.application.mcp

data class RevokeMcpTokenCommand(
    val tokenId: Long,
    val requesterId: Long,
)
