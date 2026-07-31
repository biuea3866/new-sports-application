package com.sportsapp.infrastructure.realtime

import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.gateway.TypingEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.time.ZonedDateTime

class MessageBroadcastGatewayImplTest : BehaviorSpec({

    val simpMessagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    val gateway = MessageBroadcastGatewayImpl(simpMessagingTemplate)

    Given("broadcast 를 호출하면") {
        val message = BroadcastMessage(messageId = 1L, userId = 10L, content = "안녕", createdAt = ZonedDateTime.now())

        When("roomId 가 5 이면") {
            gateway.broadcast(5L, message)

            Then("/topic/rooms/5 로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5", message) }
            }
        }
    }

    Given("broadcastTyping 을 호출하면") {
        val event = TypingEvent(userId = 10L, typing = true)

        When("roomId 가 5 이면") {
            gateway.broadcastTyping(5L, event)

            Then("/topic/rooms/5/typing 으로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5/typing", event) }
            }
        }
    }

    Given("broadcastRead 를 호출하면") {
        val event = ReadEvent(userId = 10L, lastReadMessageId = 99L)

        When("roomId 가 5 이면") {
            gateway.broadcastRead(5L, event)

            Then("/topic/rooms/5/read 로 발행한다") {
                verify { simpMessagingTemplate.convertAndSend("/topic/rooms/5/read", event) }
            }
        }
    }
})
