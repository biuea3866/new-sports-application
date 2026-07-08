package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.gateway.BroadcastMessage
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.TypingEvent
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * MessageDomainService 가 [MessageBroadcastGateway] 로 팬아웃을 위임하는지 검증한다 (BE-04 정정).
 * presentation(EventWorker/StompController)은 UseCase를 거쳐 이 메서드만 호출해야 하고,
 * Gateway 호출은 DomainService 내부에 캡슐화된다.
 */
class MessageDomainServiceBroadcastTest : BehaviorSpec({

    val roomRepository = mockk<RoomRepository>()
    val messageRepository = mockk<MessageRepository>()
    val roomParticipantRepository = mockk<RoomParticipantRepository>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
    val messageDomainService = MessageDomainService(
        roomRepository = roomRepository,
        messageRepository = messageRepository,
        roomParticipantRepository = roomParticipantRepository,
        domainEventPublisher = domainEventPublisher,
        messageBroadcastGateway = messageBroadcastGateway,
    )

    Given("커밋된 메시지를 브로드캐스트할 때") {
        val sentAt = ZonedDateTime.now()

        When("broadcastMessage 를 호출하면") {
            messageDomainService.broadcastMessage(
                roomId = 1L,
                messageId = 7L,
                senderId = 10L,
                content = "실시간 안녕",
                sentAt = sentAt,
            )

            Then("MessageBroadcastGateway.broadcast 가 1회 호출된다") {
                verify(exactly = 1) {
                    messageBroadcastGateway.broadcast(
                        1L,
                        BroadcastMessage(messageId = 7L, userId = 10L, content = "실시간 안녕", createdAt = sentAt),
                    )
                }
            }
        }
    }

    Given("타이핑 신호를 브로드캐스트할 때") {
        When("broadcastTyping 을 호출하면") {
            messageDomainService.broadcastTyping(roomId = 2L, userId = 20L, typing = true)

            Then("MessageBroadcastGateway.broadcastTyping 이 1회 호출된다") {
                verify(exactly = 1) {
                    messageBroadcastGateway.broadcastTyping(2L, TypingEvent(userId = 20L, typing = true))
                }
            }
        }
    }
})
