package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.user.UserPrincipal
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
        return principal.hasRole(UserRoleName.FACILITY_OWNER) && principal.id == userId
    }

    /**
     * MCP 토큰으로 인증된 principal 이 주어진 scope 를 보유하는지 확인한다.
     * 잘못된 scope 문자열(파싱 실패)은 예외를 던지지 않고 silent false 로 처리한다.
     */
    fun hasMcpScope(scope: String): Boolean {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication?.principal as? McpAuthenticatedPrincipal ?: return false
        val parsedScope = runCatching { McpScope.of(scope) }.getOrNull()
        return parsedScope != null && principal.grantedScopes.contains(parsedScope)
    }

    private fun currentPrincipal(): UserPrincipal? =
        SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
}
