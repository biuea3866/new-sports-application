package com.sportsapp.application.user

import com.sportsapp.domain.user.User
import com.sportsapp.domain.user.UserStatus
import java.time.ZonedDateTime

data class GetMyProfileResponse(
    val id: Long,
    val email: String,
    val status: UserStatus,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(user: User): GetMyProfileResponse = GetMyProfileResponse(
            id = user.id,
            email = user.email,
            status = user.status,
            createdAt = user.createdAt,
        )
    }
}
