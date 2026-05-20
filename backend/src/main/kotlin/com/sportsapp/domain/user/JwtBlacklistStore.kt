package com.sportsapp.domain.user

import java.time.Duration

interface JwtBlacklistStore {
    fun add(jti: String, ttl: Duration)
    fun isBlacklisted(jti: String): Boolean
}
