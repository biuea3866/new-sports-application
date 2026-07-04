package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.infrastructure.featureflag.local.LocalFeatureFlagStore
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import org.springframework.data.redis.connection.DefaultMessage

/**
 * `FeatureFlagChangeSubscriber` — 수신 시 LocalStore.refresh 호출 + 전파 지연 기록 검증.
 * (도메인/UseCase 미경유, 캐시 갱신 전용 — Repository는 LocalStore 폴백에만 존재)
 */
class FeatureFlagChangeSubscriberTest : BehaviorSpec({

    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    fun messageFor(flagKey: String, occurredAt: ZonedDateTime): DefaultMessage {
        val body = objectMapper.writeValueAsString(FeatureFlagChangeMessage(flagKey, occurredAt))
        return DefaultMessage(FeatureFlagRedisKeys.CHANGE_CHANNEL.toByteArray(), body.toByteArray())
    }

    Given("변경 메시지를 수신하면") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>(relaxed = true)
        val meterRegistry = SimpleMeterRegistry()
        val subscriber = FeatureFlagChangeSubscriber(localFeatureFlagStore, objectMapper, meterRegistry)
        val occurredAt = ZonedDateTime.now().minusSeconds(2)

        When("onMessage가 호출되면") {
            subscriber.onMessage(messageFor("demo.feature.hello", occurredAt), null)

            Then("LocalFeatureFlagStore.refresh(flagKey)가 호출된다") {
                verify(exactly = 1) { localFeatureFlagStore.refresh("demo.feature.hello") }
            }

            Then("전파 지연(occurredAt~수신 시각)이 feature_flag_propagation_lag_seconds 로 기록된다") {
                val timer = meterRegistry.find("feature_flag_propagation_lag_seconds").timer()
                requireNotNull(timer) { "전파 지연 타이머가 등록되지 않았다" }
                timer.count() shouldBe 1L
                timer.totalTime(TimeUnit.MILLISECONDS) shouldBeGreaterThan 0.0
            }
        }
    }

    Given("동일 key의 변경 메시지를 2회 수신하면") {
        val localFeatureFlagStore = mockk<LocalFeatureFlagStore>(relaxed = true)
        every { localFeatureFlagStore.refresh(any()) } returns Unit
        val meterRegistry = SimpleMeterRegistry()
        val subscriber = FeatureFlagChangeSubscriber(localFeatureFlagStore, objectMapper, meterRegistry)
        val occurredAt = ZonedDateTime.now().minusSeconds(1)

        When("onMessage를 두 번 호출하면") {
            subscriber.onMessage(messageFor("demo.feature.idempotent", occurredAt), null)
            subscriber.onMessage(messageFor("demo.feature.idempotent", occurredAt), null)

            Then("refresh가 멱등하게 두 번 모두 동일한 key로 호출된다") {
                verify(exactly = 2) { localFeatureFlagStore.refresh("demo.feature.idempotent") }
            }
        }
    }
})
