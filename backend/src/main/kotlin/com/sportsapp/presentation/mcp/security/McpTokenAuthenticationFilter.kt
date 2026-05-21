package com.sportsapp.presentation.mcp.security

import com.sportsapp.domain.common.PermissionRepository
import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.mcp.McpTokenRepository
import com.sportsapp.domain.mcp.McpTokenScopeRepository
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class McpTokenAuthenticationFilter(
    private val mcpTokenRepository: McpTokenRepository,
    private val mcpTokenScopeRepository: McpTokenScopeRepository,
    private val permissionRepository: PermissionRepository,
    private val passwordEncoder: PasswordEncoder,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val plainToken = resolveToken(request)
        val tokenId = plainToken?.let { parseTokenId(it) }

        when {
            plainToken == null || tokenId == null -> filterChain.doFilter(request, response)
            !authenticate(plainToken, tokenId, response) -> Unit
            else -> filterChain.doFilter(request, response)
        }
    }

    private fun authenticate(plainToken: String, tokenId: Long, response: HttpServletResponse): Boolean {
        val mcpToken = mcpTokenRepository.findById(tokenId)
        if (mcpToken == null || !passwordEncoder.matches(plainToken, mcpToken.tokenHash)) {
            writeUnauthorized(response)
            return false
        }
        return try {
            mcpToken.requireActive()
            mcpToken.requireNotExpired()
            injectSecurityContext(mcpToken.id, mcpToken.userId)
            true
        } catch (exception: RuntimeException) {
            writeUnauthorized(response)
            false
        }
    }

    private fun injectSecurityContext(tokenId: Long, userId: Long) {
        val grantedScopes = resolveScopes(tokenId)
        val principal = McpUserPrincipal(tokenId = tokenId, userId = userId, grantedScopes = grantedScopes)
        val authorities = grantedScopes.map { scope ->
            SimpleGrantedAuthority("MCP_SCOPE_${scope.verb.uppercase()}_${scope.domain.uppercase()}")
        }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, authorities)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization") ?: return null
        return bearerToken.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
    }

    // Token format: mcp_<id>_<random>. Returns null when prefix does not match (pass-through).
    private fun parseTokenId(plainToken: String): Long? =
        plainToken.takeIf { it.startsWith("mcp_") }
            ?.removePrefix("mcp_")
            ?.split("_", limit = 2)
            ?.takeIf { it.size >= 2 }
            ?.get(0)
            ?.toLongOrNull()

    private fun resolveScopes(tokenId: Long): Set<McpScope> {
        val tokenScopes = mcpTokenScopeRepository.findByTokenId(tokenId)
        return tokenScopes.mapNotNull { tokenScope ->
            val permission = permissionRepository.findById(tokenScope.permissionId) ?: return@mapNotNull null
            parseScopeFromPermissionName(permission.name)
        }.toSet()
    }

    private fun parseScopeFromPermissionName(permissionName: String): McpScope? {
        // permission name format: mcp.{domain}.{verb}.{qualifier}
        val parts = permissionName.split(".")
        if (parts.size < 4 || parts[0] != "mcp") return null
        return McpScope(
            verb = parts[2],
            domain = parts[1],
            qualifier = parts[3].takeIf { it != "own" },
        )
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            """{"status":401,"title":"Unauthorized","detail":"Invalid or expired MCP token"}""",
        )
    }
}
