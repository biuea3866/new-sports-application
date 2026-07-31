package com.sportsapp.domain.user.vo

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
)
