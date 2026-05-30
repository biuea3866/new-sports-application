package com.sportsapp.application.user

import com.sportsapp.domain.user.UserStatus
import com.sportsapp.domain.user.UserWithRoles
import java.time.ZonedDateTime

data class ListUsersResponse(
    val userId: Long,
    val email: String,
    val status: UserStatus,
    val roleNames: List<String>,
    val joinedAt: ZonedDateTime,
) {
    companion object {
        fun of(userWithRoles: UserWithRoles): ListUsersResponse =
            ListUsersResponse(
                userId = userWithRoles.userId,
                email = userWithRoles.email,
                status = userWithRoles.status,
                roleNames = userWithRoles.roleNames,
                joinedAt = userWithRoles.joinedAt,
            )
    }
}
