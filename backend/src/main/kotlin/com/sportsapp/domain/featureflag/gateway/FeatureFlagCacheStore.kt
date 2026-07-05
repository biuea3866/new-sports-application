package com.sportsapp.domain.featureflag.gateway

import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot

/**
 * Redis look-aside 캐시 계약 (키·TTL 계약: `docs/feature-flag-redis-contract.md`).
 */
interface FeatureFlagCacheStore {
    fun put(snapshot: FeatureFlagSnapshot)
    fun get(key: String): FeatureFlagSnapshot?
    fun evict(key: String)
}
