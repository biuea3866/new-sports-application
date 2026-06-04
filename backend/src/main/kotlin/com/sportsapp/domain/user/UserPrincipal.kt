package com.sportsapp.domain.user

import com.sportsapp.domain.common.UserRoleName

data class UserPrincipal(
    val id: Long,
    val email: String,
    val roles: List<String>,
) {
    fun hasRole(role: UserRoleName): Boolean = roles.contains(role.name)
}
