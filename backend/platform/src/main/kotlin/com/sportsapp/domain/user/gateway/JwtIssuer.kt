package com.sportsapp.domain.user.gateway

import java.time.ZonedDateTime

interface JwtIssuer {
    fun generateAccessToken(userId: Long, email: String, roles: List<String>): String
    fun generateRefreshToken(): String
    fun validateToken(token: String): Boolean
    fun extractUserId(token: String): Long
    fun extractEmail(token: String): String
    fun extractRoles(token: String): List<String>
    fun extractJti(token: String): String
    /**
     * 토큰 만료 시각. JWT 규격은 epoch 초를 쓰지만 그 변환은 infrastructure 어댑터의 몫이고,
     * 도메인 계약은 레포 표준 시간 타입으로 고정한다 (W1-DEBT-01).
     */
    fun extractExpiration(token: String): ZonedDateTime
    fun accessTokenExpiresInSeconds(): Long
}
