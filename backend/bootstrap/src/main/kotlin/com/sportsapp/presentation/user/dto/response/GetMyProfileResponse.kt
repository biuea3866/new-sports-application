package com.sportsapp.presentation.user.dto.response

import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserStatus
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
