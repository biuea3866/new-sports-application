package com.sportsapp.domain.user.dto

import com.sportsapp.domain.user.entity.UserStatus
import java.time.ZonedDateTime

data class UserWithRoles(
    val userId: Long,
    val email: String,
    val status: UserStatus,
    val roleNames: List<String>,
    val joinedAt: ZonedDateTime,
)
