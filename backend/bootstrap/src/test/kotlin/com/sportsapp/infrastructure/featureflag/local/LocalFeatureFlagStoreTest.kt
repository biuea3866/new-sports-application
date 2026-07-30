package com.sportsapp.infrastructure.featureflag.local

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagRedisKeys
import com.sportsapp.infrastructure.featureflag.redis.RedisFeatureFlagCacheStore
import com.sportsapp.infrastructure.featureflag.sampleFeatureFlag
import com.sportsapp.infrastructure.featureflag.sampleFeatureFlagSnapshot
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * `LocalFeatureFlagStore` — L1 인메모리 + Redis 캐시 → Repository 폴백 체인 검증.
 *
 * Redis는 Testcontainers 실 인스턴스, `FeatureFlagRepository`는 이 워크트리에 구현체(BE-02)가
 * 없는 도메인 interface라 MockK로 대체한다(티켓 의존: "컴파일은 domain interface(BE-01)만").
 */
class LocalFeatureFlagStoreTest : BehaviorSpec({

    val connectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
    ).apply { afterPropertiesSet() }
    val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    afterSpec { connectionFactory.destroy() }

    fun newCacheStore(meterRegistry: SimpleMeterRegistry = SimpleMeterRegistry()) =
        RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)

    Given("findAllActive가 활성 플래그 2건을 반환할 때") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val flagA = sampleFeatureFlag(key = "demo.feature.bootstrap-a")
        val flagB = sampleFeatureFlag(key = "demo.feature.bootstrap-b")
        every { featureFlagRepository.findAllActive() } returns listOf(flagA, flagB)
        val cacheStore = newCacheStore()
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, SimpleMeterRegistry())

        When("bootstrap을 호출하면") {
            localStore.bootstrap()

            Then("두 플래그가 모두 로컬에 적재된다") {
                localStore.get("demo.feature.bootstrap-a") shouldBe flagA.toSnapshot()
                localStore.get("demo.feature.bootstrap-b") shouldBe flagB.toSnapshot()
            }

            Then("두 플래그가 모두 Redis 캐시에도 적재된다") {
                cacheStore.get("demo.feature.bootstrap-a") shouldBe flagA.toSnapshot()
                cacheStore.get("demo.feature.bootstrap-b") shouldBe flagB.toSnapshot()
            }
        }
    }

    Given("로컬에 없는 key를 조회하면") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val localStore = LocalFeatureFlagStore(newCacheStore(), featureFlagRepository, SimpleMeterRegistry())

        When("get을 호출하면") {
            val result = localStore.get("demo.feature.never-refreshed")

            Then("null을 반환한다") {
                result.shouldBeNull()
            }
        }
    }

    Given("Redis 캐시에 이미 최신 스냅샷이 있는 상태에서(로컬 미스)") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val cacheStore = newCacheStore()
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.cache-hit-fallback")
        cacheStore.put(snapshot)
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, SimpleMeterRegistry())

        When("refresh(key)를 호출하면") {
            localStore.refresh(snapshot.key)

            Then("Redis 캐시를 먼저 조회해 로컬에 채우고 Repository는 호출하지 않는다") {
                localStore.get(snapshot.key) shouldBe snapshot
                verify(exactly = 0) { featureFlagRepository.findByKey(any()) }
            }
        }
    }

    Given("Redis 캐시가 미스인 상태에서(로컬 미스)") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val flag = sampleFeatureFlag(key = "demo.feature.repository-fallback")
        every { featureFlagRepository.findByKey("demo.feature.repository-fallback") } returns flag
        val cacheStore = newCacheStore()
        stringRedisTemplate.unlink(FeatureFlagRedisKeys.cacheKey(flag.flagKey))
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, SimpleMeterRegistry())

        When("refresh(key)를 호출하면") {
            localStore.refresh(flag.flagKey)

            Then("Repository로 폴백해 조회한 값을 로컬·캐시 모두에 채운다") {
                localStore.get(flag.flagKey) shouldBe flag.toSnapshot()
                cacheStore.get(flag.flagKey) shouldBe flag.toSnapshot()
                verify(exactly = 1) { featureFlagRepository.findByKey(flag.flagKey) }
            }
        }
    }

    Given("동일 key를 refresh로 2회 반영하면(pub/sub 중복 수신 시뮬레이션)") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val cacheStore = newCacheStore()
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.idempotent-refresh")
        cacheStore.put(snapshot)
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, SimpleMeterRegistry())

        When("refresh를 두 번 호출하면") {
            localStore.refresh(snapshot.key)
            val firstResult = localStore.get(snapshot.key)
            localStore.refresh(snapshot.key)
            val secondResult = localStore.get(snapshot.key)

            Then("로컬 상태가 두 번 모두 동일하다") {
                firstResult.shouldNotBeNull()
                firstResult shouldBe secondResult
            }
        }
    }

    Given("로컬 캐시 접근이 발생하면") {
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        val cacheStore = newCacheStore()
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.local-metrics")
        cacheStore.put(snapshot)
        val meterRegistry = SimpleMeterRegistry()
        val localStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, meterRegistry)
        localStore.refresh(snapshot.key)

        When("존재하는 key와 존재하지 않는 key를 각각 조회하면") {
            localStore.get(snapshot.key)
            localStore.get("demo.feature.local-metrics-missing")

            Then("layer=local,result=hit / result=miss 카운터가 각각 증가한다") {
                meterRegistry.counter(
                    "feature_flag_cache_access_total",
                    "layer",
                    "local",
                    "result",
                    "hit",
                ).count() shouldBe 1.0
                meterRegistry.counter(
                    "feature_flag_cache_access_total",
                    "layer",
                    "local",
                    "result",
                    "miss",
                ).count() shouldBe 1.0
            }
        }
    }
})
