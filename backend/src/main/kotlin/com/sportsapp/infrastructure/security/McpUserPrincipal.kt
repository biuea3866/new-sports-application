package com.sportsapp.infrastructure.security

import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope

data class McpUserPrincipal(
    override val tokenId: Long,
    override val userId: Long,
    override val grantedScopes: Set<McpScope>,
) : McpAuthenticatedPrincipal
