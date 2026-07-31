package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * W1-07 — 실시간 릴레이(Redis pub/sub)는 전달 보장이 없다(구독자 부재·연결 단절 시 유실 가능).
 * 메시지 영속화([MessageDomainService.sendMessage])는 [MessageBroadcastGateway] 호출과 완전히
 * 분리된 별도 트랜잭션·별도 시점(AFTER_COMMIT 이후 EventWorker 경유)에서 일어나므로, 실시간
 * 전달이 유실되어도 데이터 유실이 아니다 — 재접속 시 listMessages() 조회로 항상 복구된다.
 */
class MessageDomainServiceSendMessagePersistenceIndependentOfBroadcastTest : BehaviorSpec({

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

    Given("참여자가 메시지를 전송할 때") {
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
            val result = messageDomainService.sendMessage(roomId = 1L, userId = 10L, content = "안녕하세요")

            Then("MessageRepository 에 영속화가 완료된다 — 실시간 브로드캐스트 성공 여부와 무관") {
                verify(exactly = 1) { messageRepository.save(any()) }
                result shouldBe savedMessage
            }

            Then("이 호출은 MessageBroadcastGateway 를 전혀 사용하지 않는다 — 실시간 전달은 커밋 이후 별도 경로(EventWorker)다") {
                verify(exactly = 0) { messageBroadcastGateway.broadcast(any(), any()) }
                verify(exactly = 0) { messageBroadcastGateway.broadcastTyping(any(), any()) }
                verify(exactly = 0) { messageBroadcastGateway.broadcastRead(any(), any()) }
            }
        }
    }

    Given("실시간 브로드캐스트 게이트웨이가 항상 실패하는 상황에서") {
        every { messageBroadcastGateway.broadcast(any(), any()) } throws IllegalStateException("Redis 연결 단절")

        When("커밋된 메시지를 broadcastMessage 로 팬아웃하면") {
            Then("게이트웨이 예외가 발생하지만, 이는 이미 영속화가 끝난 메시지에는 영향이 없다 (호출부(EventWorker)에서 로그로 흡수)") {
                io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                    messageDomainService.broadcastMessage(
                        roomId = 1L,
                        messageId = 7L,
                        senderId = 10L,
                        content = "안녕하세요",
                        sentAt = ZonedDateTime.now(),
                    )
                }
            }
        }
    }
})
