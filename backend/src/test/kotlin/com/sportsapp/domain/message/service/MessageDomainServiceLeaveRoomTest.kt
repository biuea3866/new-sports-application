package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.gateway.MessageBroadcastGateway
import com.sportsapp.domain.message.repository.MessageRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class MessageDomainServiceLeaveRoomTest : BehaviorSpec({

    Given("마지막 참가자가 탈퇴하는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val messageRepository = mockk<MessageRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val service = MessageDomainService(
            roomRepository,
            messageRepository,
            roomParticipantRepository,
            domainEventPublisher,
            messageBroadcastGateway,
        )

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room = room, userId = 1L)

        every { roomRepository.findById(1L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 1L) } returns participant
        every { roomParticipantRepository.save(any()) } returns participant
        every { roomParticipantRepository.findActiveByRoomId(1L) } returns emptyList()
        justRun { messageRepository.softDeleteAllByRoomId(1L, 1L) }
        every { roomRepository.save(any()) } returns room

        When("leaveRoom 을 호출하면") {
            service.leaveRoom(roomId = 1L, userId = 1L)

            Then("Room 과 Message 가 함께 삭제된다") {
                verify { messageRepository.softDeleteAllByRoomId(1L, 1L) }
                verify { roomRepository.save(any()) }
            }
        }
    }

    Given("참가자가 남아있는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val messageRepository = mockk<MessageRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val service = MessageDomainService(
            roomRepository,
            messageRepository,
            roomParticipantRepository,
            domainEventPublisher,
            messageBroadcastGateway,
        )

        val room = Room.createGroup("테스트")
        val participant = RoomParticipant.create(room = room, userId = 1L)
        val remaining = listOf(RoomParticipant.create(room = room, userId = 2L))

        every { roomRepository.findById(2L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(2L, 1L) } returns participant
        every { roomParticipantRepository.save(any()) } returns participant
        every { roomParticipantRepository.findActiveByRoomId(2L) } returns remaining

        When("leaveRoom 을 호출하면") {
            service.leaveRoom(roomId = 2L, userId = 1L)

            Then("Room 과 Message 는 삭제되지 않는다") {
                verify(exactly = 0) { messageRepository.softDeleteAllByRoomId(any(), any()) }
                verify(exactly = 0) { roomRepository.save(any()) }
            }
        }
    }

    Given("참가자가 아닌 사용자가 탈퇴 요청하는 경우") {
        val roomRepository = mockk<RoomRepository>()
        val messageRepository = mockk<MessageRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val messageBroadcastGateway = mockk<MessageBroadcastGateway>(relaxed = true)
        val service = MessageDomainService(
            roomRepository,
            messageRepository,
            roomParticipantRepository,
            domainEventPublisher,
            messageBroadcastGateway,
        )

        val room = Room.createDirect()
        every { roomRepository.findById(3L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(3L, 99L) } returns null

        When("leaveRoom 을 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    service.leaveRoom(roomId = 3L, userId = 99L)
                }
            }
        }
    }
})
