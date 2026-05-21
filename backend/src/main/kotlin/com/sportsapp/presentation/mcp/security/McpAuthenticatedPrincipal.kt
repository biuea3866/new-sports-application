package com.sportsapp.presentation.mcp.security

interface McpAuthenticatedPrincipal {
    val tokenId: Long
    val userId: Long
    val grantedScopes: Set<String>
}
