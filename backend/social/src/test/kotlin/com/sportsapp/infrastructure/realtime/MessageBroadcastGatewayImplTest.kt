package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * [MessageBroadcastGatewayImpl] 검증 (W1-07 개정) — 로컬 세션 전달([LocalStompBroadcaster])과
 * 인스턴스 간 릴레이 발행([RealtimeRelayPublisher])을 모두 수행하는지, 발행 envelope 에 이
 * 인스턴스의 [RelayInstanceId]가 담기는지 검증한다.
 *
 * `Given`마다 mock을 새로 만든다 — BehaviorSpec은 기본적으로 스펙 인스턴스를 하나만 만들어
 * 최상위 `val`을 모든 leaf 테스트가 공유하므로, 상위에서 mock을 공유하면 slot capture 시
 * 이전 `Given`의 호출 이력까지 섞여(MockKException: verified more than once) 검증이 깨진다.
 */
class MessageBroadcastGatewayImplTest : BehaviorSpec({

    fun newGateway(): Triple<LocalStompBroadcaster, RealtimeRelayPublisher, MessageBroadcastGatewayImpl> {
        val localStompBroadcaster = mockk<LocalStompBroadcaster>(relaxed = true)
        val realtimeRelayPublisher = mockk<RealtimeRelayPublisher>(relaxed = true)
        val relayInstanceId = RelayInstanceId(value = "instance-under-test")
        val gateway = MessageBroadcastGatewayImpl(localStompBroadcaster, realtimeRelayPublisher, relayInstanceId)
        return Triple(localStompBroadcaster, realtimeRelayPublisher, gateway)
    }

    Given("broadcast 를 호출하면") {
        val (localStompBroadcaster, realtimeRelayPublisher, gateway) = newGateway()
        val message = BroadcastMessage(messageId = 1L, userId = 10L, content = "안녕", createdAt = ZonedDateTime.now())

        When("roomId 가 5 이면") {
            gateway.broadcast(5L, message)

            Then("로컬 세션에 즉시 전달한다") {
                verify { localStompBroadcaster.sendMessage(5L, message) }
            }

            Then("이 인스턴스 식별자를 담아 Redis 릴레이로도 발행한다") {
                val captured = slot<RealtimeRelayEnvelope>()
                verify(exactly = 1) { realtimeRelayPublisher.publish(capture(captured)) }
                val envelope = captured.captured as RealtimeRelayEnvelope.MessageBroadcast
                envelope.senderInstanceId shouldBe "instance-under-test"
                envelope.roomId shouldBe 5L
                envelope.messageId shouldBe 1L
                envelope.userId shouldBe 10L
                envelope.content shouldBe "안녕"
            }
        }
    }

    Given("broadcastTyping 을 호출하면") {
        val (localStompBroadcaster, realtimeRelayPublisher, gateway) = newGateway()
        val event = TypingEvent(userId = 10L, typing = true)

        When("roomId 가 5 이면") {
            gateway.broadcastTyping(5L, event)

            Then("로컬 세션에 즉시 전달한다") {
                verify { localStompBroadcaster.sendTyping(5L, event) }
            }

            Then("Redis 릴레이로도 발행한다") {
                val captured = slot<RealtimeRelayEnvelope>()
                verify(exactly = 1) { realtimeRelayPublisher.publish(capture(captured)) }
                val envelope = captured.captured as RealtimeRelayEnvelope.TypingBroadcast
                envelope.senderInstanceId shouldBe "instance-under-test"
                envelope.roomId shouldBe 5L
                envelope.userId shouldBe 10L
                envelope.typing shouldBe true
            }
        }
    }

    Given("broadcastRead 를 호출하면") {
        val (localStompBroadcaster, realtimeRelayPublisher, gateway) = newGateway()
        val event = ReadEvent(userId = 10L, lastReadMessageId = 99L)

        When("roomId 가 5 이면") {
            gateway.broadcastRead(5L, event)

            Then("로컬 세션에 즉시 전달한다") {
                verify { localStompBroadcaster.sendRead(5L, event) }
            }

            Then("Redis 릴레이로도 발행한다") {
                val captured = slot<RealtimeRelayEnvelope>()
                verify(exactly = 1) { realtimeRelayPublisher.publish(capture(captured)) }
                val envelope = captured.captured as RealtimeRelayEnvelope.ReadBroadcast
                envelope.senderInstanceId shouldBe "instance-under-test"
                envelope.roomId shouldBe 5L
                envelope.userId shouldBe 10L
                envelope.lastReadMessageId shouldBe 99L
            }
        }
    }
})
