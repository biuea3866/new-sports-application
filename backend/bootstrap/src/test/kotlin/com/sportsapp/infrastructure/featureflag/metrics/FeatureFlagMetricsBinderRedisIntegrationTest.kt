package com.sportsapp.infrastructure.featureflag.metrics

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.infrastructure.config.FeatureFlagRedisPubSubConfig
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import com.sportsapp.infrastructure.featureflag.redis.FeatureFlagChangeSubscriber
import com.sportsapp.infrastructure.featureflag.redis.RedisFeatureFlagCacheStore
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.seconds
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * `feature_flag_pubsub_listener_active`·`feature_flag_pubsub_connections` 게이지가
 * 실제 `RedisMessageListenerContainer`(Testcontainers Redis)의 기동 상태를 반영하는지 검증한다.
 *
 * Mock-only 로는 "실제 컨테이너 기동" 자체를 증명할 수 없어 실 Redis 인스턴스를 사용한다.
 */
class FeatureFlagMetricsBinderRedisIntegrationTest : BehaviorSpec({

    Given("RedisMessageListenerContainer가 아직 기동하지 않은 상태에서") {
        val connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
        ).apply { afterPropertiesSet() }
        val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
        val subscriberMeterRegistry = SimpleMeterRegistry()
        val featureFlagRepository = mockk<FeatureFlagRepository>()
        every { featureFlagRepository.findAllActive() } returns emptyList()
        val cacheStore = RedisFeatureFlagCacheStore(stringRedisTemplate, objectMapper, subscriberMeterRegistry)
        val localFeatureFlagStore = LocalFeatureFlagStore(cacheStore, featureFlagRepository, subscriberMeterRegistry)
        localFeatureFlagStore.bootstrap()
        val subscriber = FeatureFlagChangeSubscriber(localFeatureFlagStore, objectMapper, subscriberMeterRegistry)

        val container = FeatureFlagRedisPubSubConfig()
            .featureFlagRedisMessageListenerContainer(connectionFactory, subscriber)
            .apply { afterPropertiesSet() }

        val binderMeterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(binderMeterRegistry, localFeatureFlagStore, container)
        binder.bindGauges()

        Then("listener_active·connections 게이지가 0이다") {
            binderMeterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_LISTENER_ACTIVE_GAUGE).gauge().value() shouldBe 0.0
            binderMeterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_CONNECTIONS_GAUGE).gauge().value() shouldBe 0.0
        }

        When("container.start()로 실제 구독을 시작하면") {
            container.start()

            Then("listener_active 게이지가 실제 기동 상태를 반영해 1이 된다") {
                eventually(5.seconds) {
                    binderMeterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_LISTENER_ACTIVE_GAUGE).gauge().value() shouldBe 1.0
                }
            }

            Then("전용 구독 커넥션 수 게이지가 0보다 크다") {
                eventually(5.seconds) {
                    binderMeterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_CONNECTIONS_GAUGE).gauge().value() shouldBeGreaterThan 0.0
                }
            }

            container.stop()
        }

        connectionFactory.destroy()
    }
})
