package com.sportsapp.domain.user.repository

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String)
    fun findUserIdByToken(refreshToken: String): Long?
    fun invalidate(refreshToken: String)
    fun invalidateByUserId(userId: Long)
}
