package com.sportsapp.infrastructure.persistence.audit

import com.sportsapp.domain.user.UserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.AuditorAware
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.util.Optional

@Component("mongoAuditorAware")
@Profile("!test-jpa")
class MongoAuditorAware : AuditorAware<Long> {

    override fun getCurrentAuditor(): Optional<Long> {
        val authentication = SecurityContextHolder.getContext().authentication
        val principal = authentication
            ?.takeIf { it.isAuthenticated }
            ?.principal as? UserPrincipal
        return Optional.ofNullable(principal?.id)
    }
}
