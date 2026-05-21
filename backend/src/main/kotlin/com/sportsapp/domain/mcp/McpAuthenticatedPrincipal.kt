package com.sportsapp.domain.mcp

interface McpAuthenticatedPrincipal {
    val tokenId: Long
    val userId: Long
    val grantedScopes: Set<McpScope>
}
