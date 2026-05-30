package com.sportsapp.domain.user

import java.time.ZonedDateTime

data class UserWithRoles(
    val userId: Long,
    val email: String,
    val status: UserStatus,
    val roleNames: List<String>,
    val joinedAt: ZonedDateTime,
)
