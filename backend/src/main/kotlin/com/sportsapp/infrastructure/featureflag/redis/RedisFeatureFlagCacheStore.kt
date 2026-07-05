package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import com.sportsapp.infrastructure.featureflag.metrics.FeatureFlagCacheMetrics
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * Redis look-aside 캐시 구현 (`docs/feature-flag-redis-contract.md` §1).
 *
 * `get`은 Redis 접근 실패(`DataAccessException`)를 호출부로 전파하지 않고 null(미스)로 처리한다 —
 * 호출부(`LocalFeatureFlagStore`)가 MySQL 폴백으로 이어가도록 하기 위함이다(평가 지속 전제).
 * 이때 `redis-degraded`(warning, source=feature-flag) 경보를 로그로 발신한다.
 */
@Component
class RedisFeatureFlagCacheStore(
    private val stringRedisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val meterRegistry: MeterRegistry,
) : FeatureFlagCacheStore {

    private val logger = LoggerFactory.getLogger(RedisFeatureFlagCacheStore::class.java)

    override fun put(snapshot: FeatureFlagSnapshot) {
        val json = objectMapper.writeValueAsString(snapshot)
        stringRedisTemplate.opsForValue().set(FeatureFlagRedisKeys.cacheKey(snapshot.key), json, FeatureFlagRedisKeys.CACHE_TTL)
    }

    override fun get(key: String): FeatureFlagSnapshot? {
        return try {
            val json = stringRedisTemplate.opsForValue().get(FeatureFlagRedisKeys.cacheKey(key))
            val snapshot = json?.let { objectMapper.readValue(it, FeatureFlagSnapshot::class.java) }
            FeatureFlagCacheMetrics.recordCacheAccess(meterRegistry, FeatureFlagCacheMetrics.LAYER_REDIS, hit = snapshot != null)
            snapshot
        } catch (exception: DataAccessException) {
            FeatureFlagCacheMetrics.recordCacheAccess(meterRegistry, FeatureFlagCacheMetrics.LAYER_REDIS, hit = false)
            emitRedisDegraded(key, exception)
            null
        }
    }

    override fun evict(key: String) {
        stringRedisTemplate.unlink(FeatureFlagRedisKeys.cacheKey(key))
    }

    private fun emitRedisDegraded(key: String, exception: DataAccessException) {
        logger.warn(
            "event=redis-degraded level=warning source=feature-flag flagKey={} message={}",
            key,
            exception.message,
        )
    }
}
