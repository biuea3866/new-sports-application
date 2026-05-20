package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.JwtIssuer
import com.sportsapp.domain.user.UserPrincipal
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtIssuer: JwtIssuer,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)
        if (token != null && jwtIssuer.validateToken(token)) {
            val principal = UserPrincipal(
                id = jwtIssuer.extractUserId(token),
                email = jwtIssuer.extractEmail(token),
                roles = jwtIssuer.extractRoles(token),
            )
            val authentication = UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
            )
            SecurityContextHolder.getContext().authentication = authentication
        }
        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization") ?: return null
        return bearerToken.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
    }
}
