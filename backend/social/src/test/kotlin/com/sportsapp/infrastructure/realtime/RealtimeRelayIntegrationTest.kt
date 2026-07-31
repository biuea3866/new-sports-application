package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.TypingEvent
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * 실 Redis(Testcontainers)를 공유하는 두 social 인스턴스를 흉내 내 릴레이 팬아웃·자기 발행
 * 폐기·방(room) 격리를 검증한다 (W1-07 — §8-3 완료 판정 ⑦).
 *
 * `FeatureFlagMultiInstancePropagationScenarioTest.ShadowSubscriberInstance` 패턴을 재사용 —
 * 전체 Spring 컨텍스트(JPA/Kafka 등) 없이 Redis 조각만 직접 조립해 가볍게 유지한다(호스트 자원
 * 제약 — Testcontainers 는 SharedTestContainers.redis 싱글톤 하나만 재사용, 신규 컨테이너 없음).
 */
class RealtimeRelayIntegrationTest : BehaviorSpec({

    // WRITE_DATES_AS_TIMESTAMPS/WRITE_DATES_WITH_CONTEXT_TIME_ZONE/ADJUST_DATES_TO_CONTEXT_TIME_ZONE
    // 을 끈다 — 실 빈 RealtimeRelayObjectMapper 와 동일 설정(기본값이면 ZonedDateTime 이 숫자(epoch)로
    // 직렬화되거나 UTC 로 정규화되어 원래 zone 이 손실된다).
    fun objectMapper() = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
        .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)

    class ShadowInstance(channel: String, instanceId: String) {
        val relayInstanceId = RelayInstanceId(value = instanceId)
        private val connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
        ).apply { afterPropertiesSet() }
        private val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        private val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, relayInstanceId, objectMapper())
        // 실제 빈 배선([RealtimeRelayConfig])과 동일한 조립 경로를 그대로 재사용한다
        // (FeatureFlagRedisPubSubConfig 를 직접 호출하는 ShadowSubscriberInstance 패턴과 동일).
        private val container = RealtimeRelayConfig()
            .realtimeRelayMessageListenerContainer(
                connectionFactory,
                subscriber,
                RealtimeRelayProperties(enabled = true, channel = channel),
            )
            .apply {
                afterPropertiesSet()
                start()
            }
        val publisher = RealtimeRelayPublisher(
            stringRedisTemplate,
            objectMapper(),
            RealtimeRelayProperties(enabled = true, channel = channel),
        )

        fun close() {
            container.stop()
            connectionFactory.destroy()
        }
    }

    Given("동일 Redis 채널을 공유하는 두 social 인스턴스(A, B)가 떠 있을 때") {
        val channel = "social:realtime:relay:test-${System.nanoTime()}"
        val instanceA = ShadowInstance(channel, "instance-a")
        val instanceB = ShadowInstance(channel, "instance-b")

        try {
            When("인스턴스 A가 roomId=5 메시지를 발행하면") {
                val message = BroadcastMessage(messageId = 1L, userId = 10L, content = "안녕", createdAt = ZonedDateTime.now())
                instanceA.publisher.publish(
                    RealtimeRelayEnvelope.MessageBroadcast(
                        senderInstanceId = instanceA.relayInstanceId.value,
                        roomId = 5L,
                        messageId = message.messageId,
                        userId = message.userId,
                        content = message.content,
                        createdAt = message.createdAt,
                    ),
                )

                Then("인스턴스 B에 접속한 사용자의 로컬 세션에 도달한다") {
                    eventually(3.seconds) {
                        verify(exactly = 1) { instanceB.localStompBroadcaster.sendMessage(5L, message) }
                    }
                }

                Then("발신 인스턴스 A는 자기 발행분을 폐기해 한 번만(중복 없이) 처리된다 — 로컬 직접 전달 외 릴레이 경유 재전달이 없다") {
                    eventually(3.seconds) {
                        verify(exactly = 0) { instanceA.localStompBroadcaster.sendMessage(any(), any()) }
                    }
                }
            }

            When("인스턴스 A가 다른 방(roomId=6)의 타이핑 신호를 발행하면") {
                instanceA.publisher.publish(
                    RealtimeRelayEnvelope.TypingBroadcast(instanceA.relayInstanceId.value, roomId = 6L, userId = 11L, typing = true),
                )

                Then("인스턴스 B는 roomId=6 목적지로만 전달받고, 이전 방(roomId=5)으로는 전달되지 않는다") {
                    eventually(3.seconds) {
                        verify(exactly = 1) { instanceB.localStompBroadcaster.sendTyping(6L, TypingEvent(11L, true)) }
                    }
                    verify(exactly = 0) { instanceB.localStompBroadcaster.sendTyping(5L, any()) }
                }
            }
        } finally {
            instanceA.close()
            instanceB.close()
        }
    }

    Given("구독자가 없는 채널이 있을 때") {
        val orphanChannel = "social:realtime:relay:orphan-${System.nanoTime()}"
        val connectionFactory = LettuceConnectionFactory(
            RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
        ).apply { afterPropertiesSet() }
        val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
        val publisher = RealtimeRelayPublisher(
            stringRedisTemplate,
            objectMapper(),
            RealtimeRelayProperties(enabled = true, channel = orphanChannel),
        )

        When("그 채널로 발행하면") {
            Then("구독자가 0명이어도 오류 없이 발행이 완료된다") {
                shouldNotThrowAny {
                    publisher.publish(
                        RealtimeRelayEnvelope.ReadBroadcast(senderInstanceId = "solo-instance", roomId = 1L, userId = 1L, lastReadMessageId = 1L),
                    )
                }
            }
        }
        connectionFactory.destroy()
    }
}) {
    companion object {
        init {
            SharedTestContainers.redis
        }
    }
}
