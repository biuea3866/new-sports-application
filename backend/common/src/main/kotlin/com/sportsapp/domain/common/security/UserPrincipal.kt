package com.sportsapp.domain.common.security

import com.sportsapp.domain.common.UserRoleName

data class UserPrincipal(
    val id: Long,
    val email: String,
    val roles: List<String>,
    val partnerAuthenticated: Boolean = false,
) {
    fun hasRole(role: UserRoleName): Boolean = roles.contains(role.name)
}
