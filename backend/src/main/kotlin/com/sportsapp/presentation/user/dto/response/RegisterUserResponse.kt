package com.sportsapp.presentation.user.dto.response

import com.sportsapp.domain.user.entity.User

data class RegisterUserResponse(
    val id: Long,
    val email: String,
) {
    companion object {
        fun of(user: User): RegisterUserResponse = RegisterUserResponse(
            id = user.id,
            email = user.email,
        )
    }
}
