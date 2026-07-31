package com.sportsapp.infrastructure.realtime

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.redis.connection.Message

/**
 * [RealtimeRelaySubscriber] 검증 (W1-07) — 자기 발행 메시지 폐기(중복 방지), 다른 인스턴스 발행분의
 * 로컬 전달 위임, 손상된 메시지 무시(poison message).
 */
class RealtimeRelaySubscriberTest : BehaviorSpec({

    // WRITE_DATES_AS_TIMESTAMPS 를 끈다 — 켜진 채(기본값)로는 ZonedDateTime 이 숫자(epoch) JSON 값으로
    // 직렬화되어 zone 정보가 애초에 사라진다. WRITE_DATES_WITH_CONTEXT_TIME_ZONE/
    // ADJUST_DATES_TO_CONTEXT_TIME_ZONE 도 함께 꺼서 원래 zone(예: +09:00[Asia/Seoul])이 왕복
    // 후에도 보존되게 한다(실 빈 RealtimeRelayObjectMapper 와 동일 설정).
    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .disable(SerializationFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
        .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
        .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
    val ownInstanceId = RelayInstanceId(value = "instance-b")

    fun redisMessageOf(envelope: RealtimeRelayEnvelope): Message {
        val body = objectMapper.writeValueAsBytes(envelope)
        return mockk<Message> { every { getBody() } returns body }
    }

    Given("자기 인스턴스가 발행한 envelope 를 수신하면") {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, ownInstanceId, objectMapper)
        val envelope = RealtimeRelayEnvelope.MessageBroadcast(
            senderInstanceId = ownInstanceId.value,
            roomId = 5L,
            messageId = 1L,
            userId = 10L,
            content = "안녕",
            createdAt = ZonedDateTime.now(),
        )

        When("onMessage 가 호출되면") {
            subscriber.onMessage(redisMessageOf(envelope), null)

            Then("자기 발행분이라 폐기하고 로컬 세션에 전달하지 않는다") {
                verify(exactly = 0) { localStompBroadcaster.sendMessage(any(), any()) }
            }
        }
    }

    Given("다른 인스턴스가 발행한 메시지 envelope 를 수신하면") {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, ownInstanceId, objectMapper)
        val createdAt = ZonedDateTime.now()
        val envelope = RealtimeRelayEnvelope.MessageBroadcast(
            senderInstanceId = "instance-a",
            roomId = 7L,
            messageId = 2L,
            userId = 20L,
            content = "다른 인스턴스 안녕",
            createdAt = createdAt,
        )

        When("onMessage 가 호출되면") {
            subscriber.onMessage(redisMessageOf(envelope), null)

            Then("로컬 세션에 그대로 전달한다") {
                verify(exactly = 1) {
                    localStompBroadcaster.sendMessage(
                        7L,
                        BroadcastMessage(messageId = 2L, userId = 20L, content = "다른 인스턴스 안녕", createdAt = createdAt),
                    )
                }
            }
        }
    }

    Given("다른 인스턴스가 발행한 타이핑 envelope 를 수신하면") {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, ownInstanceId, objectMapper)
        val envelope = RealtimeRelayEnvelope.TypingBroadcast(
            senderInstanceId = "instance-a",
            roomId = 8L,
            userId = 30L,
            typing = true,
        )

        When("onMessage 가 호출되면") {
            subscriber.onMessage(redisMessageOf(envelope), null)

            Then("로컬 세션에 타이핑 신호를 전달한다") {
                verify(exactly = 1) { localStompBroadcaster.sendTyping(8L, TypingEvent(userId = 30L, typing = true)) }
            }
        }
    }

    Given("다른 인스턴스가 발행한 읽음 envelope 를 수신하면") {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, ownInstanceId, objectMapper)
        val envelope = RealtimeRelayEnvelope.ReadBroadcast(
            senderInstanceId = "instance-a",
            roomId = 9L,
            userId = 40L,
            lastReadMessageId = 99L,
        )

        When("onMessage 가 호출되면") {
            subscriber.onMessage(redisMessageOf(envelope), null)

            Then("로컬 세션에 읽음 신호를 전달한다") {
                verify(exactly = 1) { localStompBroadcaster.sendRead(9L, ReadEvent(userId = 40L, lastReadMessageId = 99L)) }
            }
        }
    }

    Given("손상된(파싱 불가능한) 메시지를 수신하면") {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val subscriber = RealtimeRelaySubscriber(localStompBroadcaster, ownInstanceId, objectMapper)
        val brokenMessage = mockk<Message> {
            every { getBody() } returns "{ 이건 유효한 JSON 이 아니다".toByteArray()
        }

        When("onMessage 가 호출되면") {
            Then("예외를 던지지 않고 조용히 무시한다 (poison message)") {
                shouldNotThrowAny { subscriber.onMessage(brokenMessage, null) }
                verify(exactly = 0) { localStompBroadcaster.sendMessage(any(), any()) }
            }
        }
    }
})
