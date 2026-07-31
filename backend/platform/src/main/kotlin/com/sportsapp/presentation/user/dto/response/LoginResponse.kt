package com.sportsapp.presentation.user.dto.response

import com.sportsapp.domain.user.vo.TokenPair

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
