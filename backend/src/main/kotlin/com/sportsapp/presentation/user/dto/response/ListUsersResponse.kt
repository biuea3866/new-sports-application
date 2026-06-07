package com.sportsapp.presentation.user.dto.response

import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.dto.UserWithRoles
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
