package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import com.sportsapp.infrastructure.featureflag.sampleFeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.util.concurrent.TimeUnit
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * `RedisFeatureFlagCacheStore` — 키·TTL·hit/miss 카운터 계약 검증 (Testcontainers Redis).
 */
class RedisFeatureFlagCacheStoreTest : BehaviorSpec({

    val connectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
    ).apply { afterPropertiesSet() }
    val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    afterSpec { connectionFactory.destroy() }

    Given("캐시 스토어에 스냅샷을 저장한 뒤") {
        val meterRegistry = SimpleMeterRegistry()
        val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.put-get")
        stringRedisTemplate.unlink(FeatureFlagRedisKeys.cacheKey(snapshot.key))

        cacheStore.put(snapshot)

        When("get을 호출하면") {
            val result = cacheStore.get(snapshot.key)

            Then("저장한 스냅샷과 동일한 값을 반환한다") {
                result shouldBe snapshot
            }
        }

        When("TTL을 조회하면") {
            val ttl = stringRedisTemplate.getExpire(FeatureFlagRedisKeys.cacheKey(snapshot.key), TimeUnit.SECONDS)

            Then("600초 이하이고 0보다 크게 설정돼 있다") {
                ttl shouldBeGreaterThan 0L
                ttl shouldBeLessThanOrEqual 600L
            }
        }
    }

    Given("evict(key) 호출 후") {
        val meterRegistry = SimpleMeterRegistry()
        val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.evict")
        cacheStore.put(snapshot)

        cacheStore.evict(snapshot.key)

        When("get을 호출하면") {
            val result = cacheStore.get(snapshot.key)

            Then("null을 반환한다") {
                result.shouldBeNull()
            }
        }
    }

    Given("캐시 히트/미스가 발생하면") {
        val meterRegistry = SimpleMeterRegistry()
        val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.metrics")
        stringRedisTemplate.unlink(FeatureFlagRedisKeys.cacheKey(snapshot.key))
        cacheStore.put(snapshot)

        When("존재하는 key와 존재하지 않는 key를 각각 조회하면") {
            cacheStore.get(snapshot.key)
            cacheStore.get("demo.feature.does-not-exist")

            Then("layer=redis,result=hit / result=miss 카운터가 각각 1 이상 증가한다") {
                val hitCount = meterRegistry.counter(
                    "feature_flag_cache_access_total",
                    "layer",
                    "redis",
                    "result",
                    "hit",
                ).count()
                val missCount = meterRegistry.counter(
                    "feature_flag_cache_access_total",
                    "layer",
                    "redis",
                    "result",
                    "miss",
                ).count()

                hitCount shouldBe 1.0
                missCount shouldBe 1.0
            }
        }
    }
})
