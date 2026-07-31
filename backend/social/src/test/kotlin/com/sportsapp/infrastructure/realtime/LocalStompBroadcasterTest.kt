package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.messaging.simp.SimpMessagingTemplate

/**
 * 이 인스턴스의 로컬 WebSocket 세션에만 전달하는 [LocalStompBroadcaster] 검증 (W1-07).
 * Redis 릴레이 유무와 무관하게 항상 수행되는 경로다.
 */
class LocalStompBroadcasterTest : BehaviorSpec({

    val simpMessagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    val broadcaster = LocalStompBroadcaster(simpMessagingTemplate)

    Given("sendMessage 를 호출하면") {
        val message = BroadcastMessage(messageId = 1L, userId = 10L, content = "안녕", createdAt = ZonedDateTime.now())

        When("roomId 가 5 이면") {
            broadcaster.sendMessage(5L, message)

            Then("/topic/rooms/5 로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5", message) }
            }
        }
    }

    Given("sendTyping 을 호출하면") {
        val event = TypingEvent(userId = 10L, typing = true)

        When("roomId 가 5 이면") {
            broadcaster.sendTyping(5L, event)

            Then("/topic/rooms/5/typing 으로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5/typing", event) }
            }
        }
    }

    Given("sendRead 를 호출하면") {
        val event = ReadEvent(userId = 10L, lastReadMessageId = 99L)

        When("roomId 가 5 이면") {
            broadcaster.sendRead(5L, event)

            Then("/topic/rooms/5/read 로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5/read", event) }
            }
        }
    }

    Given("서로 다른 room 으로 sendMessage 를 호출하면") {
        val messageA = BroadcastMessage(messageId = 2L, userId = 1L, content = "A", createdAt = ZonedDateTime.now())
        val messageB = BroadcastMessage(messageId = 3L, userId = 2L, content = "B", createdAt = ZonedDateTime.now())

        When("roomId 가 각각 100, 200 이면") {
            broadcaster.sendMessage(100L, messageA)
            broadcaster.sendMessage(200L, messageB)

            Then("서로 다른 목적지로 발행되어 방(room) 간 격리가 유지된다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/100", messageA) }
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/200", messageB) }
                verify(exactly = 0) { simpMessagingTemplate.convertAndSend("/topic/rooms/100", messageB) }
            }
        }
    }
})
