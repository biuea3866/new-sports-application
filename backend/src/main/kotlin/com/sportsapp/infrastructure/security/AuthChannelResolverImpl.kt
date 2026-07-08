package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.AuthChannelResolver
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class AuthChannelResolverImpl : AuthChannelResolver {

    override fun isPartnerAuthenticated(): Boolean {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: return false
        return principal.partnerAuthenticated
    }
}
