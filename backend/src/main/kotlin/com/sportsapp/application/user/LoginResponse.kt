package com.sportsapp.application.user

import com.sportsapp.domain.user.TokenPair

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresIn: Long,
) {
    companion object {
        fun of(tokenPair: TokenPair): LoginResponse = LoginResponse(
            accessToken = tokenPair.accessToken,
            refreshToken = tokenPair.refreshToken,
            accessTokenExpiresIn = tokenPair.accessTokenExpiresIn,
        )
    }
}
