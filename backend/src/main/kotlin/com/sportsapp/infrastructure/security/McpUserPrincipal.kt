package com.sportsapp.infrastructure.security

import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.vo.McpScope

data class McpUserPrincipal(
    override val tokenId: Long,
    override val userId: Long,
    override val grantedScopes: Set<McpScope>,
) : McpAuthenticatedPrincipal
