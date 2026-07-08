package com.sportsapp.infrastructure.featureflag.redis

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.SharedTestContainers
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Duration.Companion.seconds
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer

/**
 * `RedisFeatureFlagChangeBroadcaster` — `featureflag:changes` 채널 발행 계약 검증 (Testcontainers Redis).
 */
class RedisFeatureFlagChangeBroadcasterTest : BehaviorSpec({

    val connectionFactory = LettuceConnectionFactory(
        RedisStandaloneConfiguration(SharedTestContainers.redis.host, SharedTestContainers.redis.getMappedPort(6379)),
    ).apply { afterPropertiesSet() }
    val stringRedisTemplate = StringRedisTemplate(connectionFactory).apply { afterPropertiesSet() }
    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())
    val broadcaster = RedisFeatureFlagChangeBroadcaster(stringRedisTemplate, objectMapper)

    val receivedMessages = ConcurrentLinkedQueue<String>()
    val container = RedisMessageListenerContainer().apply {
        setConnectionFactory(connectionFactory)
        addMessageListener(
            MessageListener { message: Message, _ -> receivedMessages.add(String(message.body)) },
            ChannelTopic(FeatureFlagRedisKeys.CHANGE_CHANNEL),
        )
        afterPropertiesSet()
        start()
    }

    afterSpec {
        container.stop()
        connectionFactory.destroy()
    }

    Given("broadcast(key)를 호출하면") {
        val beforeBroadcast = ZonedDateTime.now()

        When("featureflag:changes 채널에 발행하면") {
            broadcaster.broadcast("demo.feature.hello")

            Then("구독자가 flagKey·occurredAt 필드를 포함한 메시지를 수신한다") {
                eventually(5.seconds) {
                    val payload = receivedMessages.find { it.contains("demo.feature.hello") }
                    requireNotNull(payload) { "채널 메시지를 수신하지 못했다" }

                    val change = objectMapper.readValue(payload, FeatureFlagChangeMessage::class.java)
                    change.flagKey shouldBe "demo.feature.hello"
                    (change.occurredAt.isAfter(beforeBroadcast.minusSeconds(1))) shouldBe true
                }
            }
        }
    }
})
