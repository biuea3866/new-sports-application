package com.sportsapp.application.user

import com.sportsapp.domain.user.User

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
