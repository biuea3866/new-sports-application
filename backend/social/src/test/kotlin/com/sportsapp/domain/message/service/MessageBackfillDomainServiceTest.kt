package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Message
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.exception.RoomParticipantExpiredException
import com.sportsapp.domain.message.repository.MessageCustomRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.vo.ParticipantType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class MessageBackfillDomainServiceTest : BehaviorSpec({

    Given("방에 참여 중인 사용자가 끊긴 구간 메시지를 backfill 요청할 때") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val service = MessageBackfillDomainService(roomParticipantRepository, messageCustomRepository)

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 10L)
        val afterMessages = listOf(
            Message.create(room, 1L, "메시지1"),
            Message.create(room, 1L, "메시지2"),
        )

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 10L) } returns participant
        every { messageCustomRepository.findAfter(1L, 120L, 30) } returns afterMessages

        When("backfill(roomId=1, userId=10, afterMessageId=120) 을 호출하면") {
            val result = service.backfill(roomId = 1L, userId = 10L, afterMessageId = 120L)

            Then("id > afterMessageId 인 메시지가 반환된다") {
                result shouldHaveSize 2
                verify(exactly = 1) { messageCustomRepository.findAfter(1L, 120L, 30) }
            }
        }
    }

    Given("끊긴 구간이 없는(최신까지 읽은) 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val service = MessageBackfillDomainService(roomParticipantRepository, messageCustomRepository)

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 20L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(2L, 20L) } returns participant
        every { messageCustomRepository.findAfter(2L, 999L, 30) } returns emptyList()

        When("backfill(roomId=2, userId=20, afterMessageId=999) 을 호출하면") {
            val result = service.backfill(roomId = 2L, userId = 20L, afterMessageId = 999L)

            Then("빈 목록을 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("방 참여자가 아닌 사용자") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val service = MessageBackfillDomainService(roomParticipantRepository, messageCustomRepository)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(3L, 99L) } returns null

        When("backfill 을 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    service.backfill(roomId = 3L, userId = 99L, afterMessageId = 10L)
                }
            }
        }
    }

    Given("만료된 게스트 참여자") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val service = MessageBackfillDomainService(roomParticipantRepository, messageCustomRepository)

        val room = Room.createDirect()
        val expiredGuest = RoomParticipant.reconstitute(
            room = room,
            userId = 30L,
            joinedAt = ZonedDateTime.now().minusDays(10),
            participantType = ParticipantType.GUEST,
            canSpeak = true,
            expiresAt = ZonedDateTime.now().minusDays(1),
            lastReadMessageId = null,
        )

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(4L, 30L) } returns expiredGuest

        When("backfill 을 호출하면") {
            Then("RoomParticipantExpiredException 이 발생한다") {
                shouldThrow<RoomParticipantExpiredException> {
                    service.backfill(roomId = 4L, userId = 30L, afterMessageId = 10L)
                }
            }
        }
    }

    Given("동일한 afterMessageId 로 재요청하는 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val messageCustomRepository = mockk<MessageCustomRepository>()
        val service = MessageBackfillDomainService(roomParticipantRepository, messageCustomRepository)

        val room = Room.createDirect()
        val participant = RoomParticipant.create(room, 40L)
        val afterMessages = listOf(Message.create(room, 1L, "메시지1"))

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(5L, 40L) } returns participant
        every { messageCustomRepository.findAfter(5L, 200L, 30) } returns afterMessages

        When("backfill(roomId=5, userId=40, afterMessageId=200) 을 두 번 호출하면") {
            val first = service.backfill(roomId = 5L, userId = 40L, afterMessageId = 200L)
            val second = service.backfill(roomId = 5L, userId = 40L, afterMessageId = 200L)

            Then("동일한 결과를 반환한다 (멱등)") {
                first.map { it.content } shouldHaveSize second.size
            }
        }
    }
})
