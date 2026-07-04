package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber
import com.sportsapp.infrastructure.featureflag.redis.RedisFeatureFlagCacheStore
import com.sportsapp.infrastructure.featureflag.redis.RedisFeatureFlagChangeBroadcaster
import com.sportsapp.infrastructure.featureflag.sampleFeatureFlagSnapshot
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * `FeatureFlagRedisPubSubConfig` 로 구성한 `RedisMessageListenerContainer`가 실제로
 * `featureflag:changes` 채널을 구독해 `FeatureFlagChangeSubscriber`까지 메시지를 전달하는지
 * end-to-end로 검증한다 (Testcontainers Redis, 신규 인프라 RedisMessageListenerContainer 최초 도입).
 */
class FeatureFlagChangePropagationIntegrationTest : BehaviorSpec({

    val connectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
    ).apply { afterPropertiesSet() }
    val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    val meterRegistry = SimpleMeterRegistry()
    val featureFlagRepository = mockk<FeatureFlagRepository>()

    val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, meterRegistry)
    val broadcaster = RedisFeatureFlagChangeBroadcaster(stringRedisTemplate, objectMapper)
    val localFeatureFlagStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, meterRegistry)
    val subscriber = FeatureFlagChangeSubscriber(localFeatureFlagStore, objectMapper, meterRegistry)

    val container = FeatureFlagRedisPubSubConfig()
        .featureFlagRedisMessageListenerContainer(connectionFactory, subscriber)
        .apply {
            afterPropertiesSet()
            start()
        }

    afterSpec {
        container.stop()
        connectionFactory.destroy()
    }

    Given("쓰기 인스턴스가 캐시를 갱신하고 변경을 broadcast하면") {
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.propagation")
        cacheStore.put(snapshot)

        When("broadcast(key)를 호출하면") {
            broadcaster.broadcast(snapshot.key)

            Then("구독 중인 FeatureFlagChangeSubscriber가 수신해 LocalStore를 갱신한다") {
                eventually(5.seconds) {
                    localFeatureFlagStore.get(snapshot.key) shouldBe snapshot
                }
            }
        }
    }

    Given("동일 key를 2회 broadcast하면") {
        val snapshot = sampleFeatureFlagSnapshot(key = "demo.feature.propagation-idempotent")
        cacheStore.put(snapshot)

        When("broadcast를 두 번 호출하면") {
            broadcaster.broadcast(snapshot.key)
            broadcaster.broadcast(snapshot.key)

            Then("두 번 수신해도 로컬 상태가 동일하게 수렴한다(멱등)") {
                eventually(5.seconds) {
                    localFeatureFlagStore.get(snapshot.key) shouldBe snapshot
                }
            }
        }
    }
})
