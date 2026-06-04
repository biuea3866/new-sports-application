package com.sportsapp.domain.user.gateway

import java.time.Duration

interface JwtBlacklistStore {
    fun add(jti: String, ttl: Duration)
    fun isBlacklisted(jti: String): Boolean
}
