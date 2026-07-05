package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.event.MessageSentEvent
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * MessageDomainService.sendMessage() 저장 후 MessageSentEvent 발행 검증 (BE-04).
 * 기존 MessageDomainServiceScenarioTest 는 그대로 두고, 신규 이벤트 발행 동작만 별도 검증한다.
 */
class MessageDomainServiceSendMessageEventTest : BehaviorSpec({

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

    Given("참여자가 메시지를 정상 전송하면") {
        val room = Room.createDirect()
        val sentAt = ZonedDateTime.now()
        val savedMessage = mockk<Message> {
            every { id } returns 7L
            every { createdAt } returns sentAt
        }
        every { roomRepository.findById(1L) } returns room
        every { roomParticipantRepository.existsByRoomIdAndUserId(1L, 10L) } returns true
        every { messageRepository.save(any()) } returns savedMessage
        every { roomRepository.save(room) } returns room

        When("sendMessage 를 호출하면") {
            messageDomainService.sendMessage(roomId = 1L, userId = 10L, content = "안녕하세요")

            Then("MessageSentEvent 가 저장 이후 1회 발행된다") {
                val captured = slot<DomainEvent>()
                verify(exactly = 1) { domainEventPublisher.publish(capture(captured)) }
                val event = captured.captured as MessageSentEvent
                event.aggregateId shouldBe 7L
                event.roomId shouldBe 1L
                event.senderId shouldBe 10L
                event.content shouldBe "안녕하세요"
                event.sentAt shouldBe sentAt
            }
        }
    }
})
