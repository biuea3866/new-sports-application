package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class OwnershipGuardImpl : OwnershipGuard {

    override fun requireOwned(ownerUserId: Long?, authUserId: Long) {
        if (ownerUserId != null && ownerUserId != authUserId) {
            throw ResourceNotFoundException("Resource", "***")
        }
    }

    override fun authUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? UserPrincipal
            ?: throw UnauthorizedException()
        return principal.id
    }
}
