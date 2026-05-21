package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.UserPrincipal
import com.sportsapp.presentation.mcp.security.McpAuthenticatedPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component("authz")
class AuthorizationExpressions {

    fun isOwner(userId: Long): Boolean {
        val principal = currentPrincipal() ?: return false
        return principal.id == userId
    }

    fun isFacilityOwner(userId: Long): Boolean {
        val principal = currentPrincipal() ?: return false
        val hasFacilityOwnerRole = principal.roles.contains("FACILITY_OWNER")
        return hasFacilityOwnerRole && principal.id == userId
    }

    fun hasMcpScope(scope: String): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? McpAuthenticatedPrincipal ?: return false
        return principal.grantedScopes.contains(scope)
    }

    private fun currentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
