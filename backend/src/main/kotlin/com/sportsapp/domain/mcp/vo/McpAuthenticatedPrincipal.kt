package com.sportsapp.domain.mcp.vo

interface McpAuthenticatedPrincipal {
    val tokenId: Long
    val userId: Long
    val grantedScopes: Set<McpScope>
}
