package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.event.RoomReadEvent
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.gateway.ReadEvent
import com.sportsapp.domain.message.repository.MessageCustomRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

/** 영속화 전 Room 은 id 가 0 으로 고정되므로, 여러 방을 구분해야 하는 테스트에서 리플렉션으로 id 를 부여한다. */
private fun Room.withId(id: Long): Room {
    val idField = Room::class.java.getDeclaredField("id")
    idField.isAccessible = true
    idField.set(this, id)
    return this
}

class ReadCursorDomainServiceTest : BehaviorSpec({

    Given("방에 참여 중인 사용자가 읽음 커서를 갱신할 때") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        val room = Room.createDirect().withId(1L)
        val participant = RoomParticipant.create(room, 10L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 10L) } returns participant
        every { roomParticipantRepository.save(participant) } returns participant

        When("markRead(roomId=1, userId=10, lastReadMessageId=100) 을 호출하면") {
            val result = service.markRead(roomId = 1L, userId = 10L, lastReadMessageId = 100L)

            Then("참여자의 읽음 커서가 100 으로 갱신되고 저장된다") {
                result.currentLastReadMessageId shouldBe 100L
                verify { roomParticipantRepository.save(participant) }
            }

            Then("커밋 이후 브로드캐스트를 위한 RoomReadEvent 가 발행된다 (동기 브로드캐스트는 하지 않는다)") {
                val eventSlot = slot<DomainEvent>()
                verify(exactly = 1) { domainEventPublisher.publish(capture(eventSlot)) }
                val event = eventSlot.captured as RoomReadEvent
                event.roomId shouldBe 1L
                event.userId shouldBe 10L
                event.lastReadMessageId shouldBe 100L
                verify(exactly = 0) { messageBroadcastGateway.broadcastRead(any(), any()) }
            }
        }
    }

    Given("이미 lastReadMessageId=60 인 참여자") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 20L)
        participant.markReadUpTo(60L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(2L, 20L) } returns participant
        every { roomParticipantRepository.save(participant) } returns participant

        When("markRead(roomId=2, userId=20, lastReadMessageId=30) 을 역행 호출하면") {
            val result = service.markRead(roomId = 2L, userId = 20L, lastReadMessageId = 30L)

            Then("커서는 60 으로 유지된다 (forward-only, 멱등)") {
                result.currentLastReadMessageId shouldBe 60L
            }
        }
    }

    Given("방 참여자가 아닌 사용자") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(3L, 99L) } returns null

        When("markRead 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    service.markRead(roomId = 3L, userId = 99L, lastReadMessageId = 10L)
                }
            }
        }

        When("unreadCount 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    service.unreadCount(roomId = 3L, userId = 99L)
                }
            }
        }
    }

    Given("lastReadMessageId=50 인 참여자의 방에 안읽은 메시지가 있을 때") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 30L)
        participant.markReadUpTo(50L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(4L, 30L) } returns participant
        every { messageCustomRepository.countUnread(4L, 50L, 30L) } returns 10L

        When("unreadCount(roomId=4, userId=30) 를 호출하면") {
            val result = service.unreadCount(roomId = 4L, userId = 30L)

            Then("10 을 반환한다") {
                result shouldBe 10L
            }
        }
    }

    Given("아직 한 번도 읽지 않은 참여자 (lastReadMessageId=null)") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 40L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(5L, 40L) } returns participant
        every { messageCustomRepository.countUnread(5L, 0L, 40L) } returns 3L

        When("unreadCount 를 호출하면") {
            val result = service.unreadCount(roomId = 5L, userId = 40L)

            Then("afterMessageId=0 기준으로 조회해 3 을 반환한다") {
                result shouldBe 3L
            }
        }
    }

    Given("내가 참여 중인 방이 여러 개일 때") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        val roomA = Room.createDirect().withId(1L)
        val roomB = Room.createDirect().withId(2L)
        val participantA = RoomParticipant.create(roomA, 50L)
        val participantB = RoomParticipant.create(roomB, 50L)
        participantA.markReadUpTo(10L)

        every { roomParticipantRepository.findActiveByUserId(50L) } returns listOf(participantA, participantB)
        every { messageCustomRepository.countUnread(roomA.id, 10L, 50L) } returns 2L
        every { messageCustomRepository.countUnread(roomB.id, 0L, 50L) } returns 0L

        When("unreadForMyRooms(50) 을 호출하면") {
            val result = service.unreadForMyRooms(50L)

            Then("방별 안읽은 수가 담긴 Map 을 반환한다") {
                result[roomA.id] shouldBe 2L
                result[roomB.id] shouldBe 0L
            }
        }
    }

    Given("커밋 이후 워커가 읽음 이벤트를 받아 브로드캐스트를 위임할 때") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = ReadCursorDomainService(
            roomParticipantRepository,
            messageCustomRepository,
            messageBroadcastGateway,
            domainEventPublisher,
        )

        When("broadcastRead(roomId=6, userId=60, lastReadMessageId=70) 를 호출하면") {
            service.broadcastRead(roomId = 6L, userId = 60L, lastReadMessageId = 70L)

            Then("MessageBroadcastGateway.broadcastRead 가 1회 호출된다") {
                val eventSlot = slot<ReadEvent>()
                verify(exactly = 1) { messageBroadcastGateway.broadcastRead(6L, capture(eventSlot)) }
                eventSlot.captured.userId shouldBe 60L
                eventSlot.captured.lastReadMessageId shouldBe 70L
            }
        }
    }
})
