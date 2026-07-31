package com.sportsapp.infrastructure.featureflag.metrics

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import java.time.Duration
import org.slf4j.LoggerFactory
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * `FeatureFlagMetricsBinder` 게이지·부트스트랩 경보 발신 단위 검증.
 *
 * `LocalFeatureFlagStore`·`RedisMessageListenerContainer`는 MockK로 대체해 순수 바인딩 로직만
 * 얇게 검증한다 — 실제 컨테이너 기동 상태 검증은
 * [FeatureFlagMetricsBinderRedisIntegrationTest](../metrics/FeatureFlagMetricsBinderRedisIntegrationTest.kt)에서
 * Testcontainers Redis로 수행한다.
 */
class FeatureFlagMetricsBinderTest : BehaviorSpec({

    Given("로컬 스토어에 스냅샷이 3건 적재된 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 3
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("게이지를 바인딩하면") {
            binder.bindGauges()

            Then("로컬 스냅샷 크기 게이지가 적재된 플래그 수(3)를 반영한다") {
                meterRegistry.get(FeatureFlagMetricsBinder.LOCAL_SNAPSHOT_SIZE_GAUGE).gauge().value() shouldBe 3.0
            }
        }
    }

    Given("로컬 스토어 상태 확인이 정상인 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 0
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("게이지를 바인딩하면") {
            binder.bindGauges()

            Then("bootstrap_success 게이지가 1이다") {
                meterRegistry.get(FeatureFlagMetricsBinder.BOOTSTRAP_SUCCESS_GAUGE).gauge().value() shouldBe 1.0
            }
        }
    }

    Given("로컬 스토어 상태 확인이 실패하는 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } throws IllegalStateException("local store unavailable")
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(FeatureFlagMetricsBinder::class.java) as Logger
        logger.addAppender(listAppender)

        When("게이지를 바인딩하면") {
            binder.bindGauges()

            Then("bootstrap_success 게이지가 0이다") {
                meterRegistry.get(FeatureFlagMetricsBinder.BOOTSTRAP_SUCCESS_GAUGE).gauge().value() shouldBe 0.0
            }

            Then("critical 경보(source=feature-flag)가 로그로 발신된다") {
                val criticalLog = requireNotNull(
                    listAppender.list.find { it.formattedMessage.contains("feature-flag-bootstrap-failed") },
                ) { "부트스트랩 실패 critical 로그가 발신되지 않았다" }

                criticalLog.level shouldBe Level.ERROR
                criticalLog.formattedMessage.contains("source=feature-flag") shouldBe true
                criticalLog.formattedMessage.contains("severity=critical") shouldBe true
            }
        }

        logger.detachAppender(listAppender)
    }

    Given("pub/sub 리스너가 기동 상태일 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 0
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns true
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("게이지를 바인딩하면") {
            binder.bindGauges()

            Then("listener_active 게이지가 1이다") {
                meterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_LISTENER_ACTIVE_GAUGE).gauge().value() shouldBe 1.0
            }

            Then("전용 구독 커넥션 수 게이지가 0보다 크다") {
                meterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_CONNECTIONS_GAUGE).gauge().value() shouldBe 1.0
            }
        }
    }

    Given("pub/sub 리스너가 정지 상태일 때") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 0
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("게이지를 바인딩하면") {
            binder.bindGauges()

            Then("listener_active 게이지가 0이다") {
                meterRegistry.get(FeatureFlagMetricsBinder.PUBSUB_LISTENER_ACTIVE_GAUGE).gauge().value() shouldBe 0.0
            }
        }
    }

    Given("subscriber가 이미 전파 지연 타이머를 기록해둔 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 0
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        Timer.builder(FeatureFlagCacheMetrics.PROPAGATION_LAG_TIMER)
            .register(meterRegistry)
            .record(Duration.ofMillis(1500))
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("propagationLagSeconds를 조회하면") {
            val lag = binder.propagationLagSeconds()

            Then("최근 수신 전파 지연 값(약 1.5초)을 노출한다") {
                lag shouldBe (1.5 plusOrMinus 0.01)
            }
        }
    }

    Given("아직 전파 지연이 한 건도 기록되지 않은 상태에서") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>()
        every { localFeatureFlagStore.size() } returns 0
        val redisMessageListenerContainer = mockk<RedisMessageListenerContainer>()
        every { redisMessageListenerContainer.isRunning } returns false
        val meterRegistry = SimpleMeterRegistry()
        val binder = FeatureFlagMetricsBinder(meterRegistry, localFeatureFlagStore, redisMessageListenerContainer)

        When("propagationLagSeconds를 조회하면") {
            val lag = binder.propagationLagSeconds()

            Then("0.0을 반환한다") {
                lag shouldBe 0.0
            }
        }
    }
})
