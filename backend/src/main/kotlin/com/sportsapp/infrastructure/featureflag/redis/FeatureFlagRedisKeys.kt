package com.sportsapp.infrastructure.featureflag.redis

import java.time.Duration

/**
 * FeatureFlag Redis 키·채널·TTL 계약 (`docs/feature-flag-redis-contract.md`).
 */
object FeatureFlagRedisKeys {
    private const val CACHE_KEY_PREFIX = "featureflag:flag:"

    const val CHANGE_CHANNEL = "featureflag:changes"

    val CACHE_TTL: Duration = Duration.ofMinutes(10)

    fun cacheKey(flagKey: String): String = "$CACHE_KEY_PREFIX$flagKey"
}
