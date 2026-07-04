package com.sportsapp.domain.featureflag.gateway

/**
 * 변경 전파(Redis pub/sub) 계약 (채널 계약: `docs/feature-flag-redis-contract.md`).
 */
interface FeatureFlagChangeBroadcaster {
    fun broadcast(key: String)
}
