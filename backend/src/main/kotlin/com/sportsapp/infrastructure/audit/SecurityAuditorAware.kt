package com.sportsapp.infrastructure.audit

import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.Optional

@Component
class SecurityAuditorAware : AuditorAware<Long> {

    override fun getCurrentAuditor(): Optional<Long> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication
            ?.takeIf { it.isAuthenticated }
            ?.principal as? UserPrincipal
        return Optional.ofNullable(principal?.id)
    }
}
