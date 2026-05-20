package com.sportsapp.domain.user

data class UserPrincipal(
    val id: Long,
    val email: String,
    val roles: List<String>,
)
