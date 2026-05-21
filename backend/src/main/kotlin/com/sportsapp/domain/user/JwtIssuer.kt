package com.sportsapp.domain.user

import java.time.Instant

interface JwtIssuer {
    fun generateAccessToken(userId: Long, email: String, roles: List<String>): String
    fun generateRefreshToken(): String
    fun validateToken(token: String): Boolean
    fun extractUserId(token: String): Long
    fun extractEmail(token: String): String
    fun extractRoles(token: String): List<String>
    fun extractJti(token: String): String
    fun extractExpiration(token: String): Instant
    fun accessTokenExpiresInSeconds(): Long
}
