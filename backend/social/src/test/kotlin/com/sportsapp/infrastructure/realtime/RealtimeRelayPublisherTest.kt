package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * [RealtimeRelayPublisher] 검증 (W1-07) — social 전용 Redis 채널 발행, 플래그 OFF 시 발행 생략,
 * Redis 장애 시 예외를 삼켜 호출부(로컬 전달)를 오염시키지 않는지.
 */
class RealtimeRelayPublisherTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    fun envelope(roomId: Long = 5L) = RealtimeRelayEnvelope.MessageBroadcast(
        senderInstanceId = "instance-a",
        roomId = roomId,
        messageId = 1L,
        userId = 10L,
        content = "안녕",
        createdAt = ZonedDateTime.now(),
    )

    Given("릴레이가 활성화(enabled=true)된 상태에서") {
        val stringRedisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val properties = RealtimeRelayProperties(enabled = true, channel = "social:realtime:relay")
        val publisher = RealtimeRelayPublisher(stringRedisTemplate, objectMapper, properties)

        When("publish 를 호출하면") {
            publisher.publish(envelope())

            Then("social 전용 채널로 JSON 직렬화된 envelope 를 발행한다") {
                val captured = slot<String>()
                verify(exactly = 1) { stringRedisTemplate.convertAndSend("social:realtime:relay", capture(captured)) }
                captured.captured shouldContain "\"eventType\":\"MESSAGE\""
                captured.captured shouldContain "\"senderInstanceId\":\"instance-a\""
            }
        }
    }

    Given("릴레이가 비활성화(enabled=false)된 상태에서 (롤백 경로)") {
        val stringRedisTemplate = mockk<StringRedisTemplate>(relaxed = true)
        val properties = RealtimeRelayProperties(enabled = false, channel = "social:realtime:relay")
        val publisher = RealtimeRelayPublisher(stringRedisTemplate, objectMapper, properties)

        When("publish 를 호출하면") {
            publisher.publish(envelope())

            Then("Redis 로는 아무것도 발행하지 않는다") {
                verify(exactly = 0) { stringRedisTemplate.convertAndSend(any(), any()) }
            }
        }
    }

    Given("Redis 연결이 끊긴 상태에서") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        every { stringRedisTemplate.convertAndSend(any(), any()) } throws RedisConnectionFailureException("연결 끊김")
        val properties = RealtimeRelayProperties(enabled = true, channel = "social:realtime:relay")
        val publisher = RealtimeRelayPublisher(stringRedisTemplate, objectMapper, properties)

        When("publish 를 호출하면") {
            Then("예외를 전파하지 않는다 — 로컬 전달은 이 실패와 무관하게 이미 끝난 상태다") {
                shouldNotThrowAny { publisher.publish(envelope()) }
            }
        }
    }

    Given("Redis 연결이 끊겼다가 복구된 상태에서") {
        val stringRedisTemplate = mockk<StringRedisTemplate>()
        var callCount = 0
        every { stringRedisTemplate.convertAndSend(any(), any()) } answers {
            callCount += 1
            if (callCount == 1) throw RedisConnectionFailureException("연결 끊김") else 1L
        }
        val properties = RealtimeRelayProperties(enabled = true, channel = "social:realtime:relay")
        val publisher = RealtimeRelayPublisher(stringRedisTemplate, objectMapper, properties)

        When("첫 publish 가 실패한 뒤 두 번째 publish 를 호출하면") {
            publisher.publish(envelope())
            publisher.publish(envelope())

            Then("두 번째 발행은 예외 없이 재시도되어 다시 인스턴스 간 전달이 가능해진다") {
                verify(exactly = 2) { stringRedisTemplate.convertAndSend(any(), any()) }
            }
        }
    }
})
