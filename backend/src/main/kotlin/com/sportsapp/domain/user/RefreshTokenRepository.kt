package com.sportsapp.domain.user

interface RefreshTokenRepository {
    fun save(userId: Long, refreshToken: String)
    fun find(userId: Long): String?
    fun remove(userId: Long)
}
