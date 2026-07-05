package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class GuestEvictionDomainServiceTest : BehaviorSpec({

    Given("만료된 게스트가 1건 존재하는 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        val room = Room.createGroup("만료 테스트 방")
        val expiredGuest = RoomParticipant.reconstitute(
            room = room,
            userId = 5L,
            joinedAt = ZonedDateTime.now().minusDays(10),
            participantType = com.sportsapp.domain.message.vo.ParticipantType.GUEST,
            canSpeak = true,
            expiresAt = ZonedDateTime.now().minusDays(1),
            lastReadMessageId = 42L,
        )

        every { roomParticipantRepository.findExpiredGuestsBefore(any()) } returns listOf(expiredGuest)
        val saved = slot<RoomParticipant>()
        every { roomParticipantRepository.save(capture(saved)) } returns expiredGuest

        When("evictExpired 를 호출하면") {
            val evictedCount = service.evictExpired()

            Then("만료된 게스트가 방출(soft-delete)되고 방출 건수를 반환한다") {
                evictedCount shouldBe 1
                saved.captured.isDeleted shouldBe true
            }

            Then("읽은 이력(lastReadMessageId)은 유지된다") {
                saved.captured.currentLastReadMessageId shouldBe 42L
            }
        }
    }

    Given("만료되지 않은 게스트와 MEMBER 만 조회 대상에 없는 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        every { roomParticipantRepository.findExpiredGuestsBefore(any()) } returns emptyList()

        When("evictExpired 를 호출하면") {
            val evictedCount = service.evictExpired()

            Then("방출 대상이 없어 0 건이 반환되고 save 는 호출되지 않는다") {
                evictedCount shouldBe 0
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("참여자 2건 중 1건 처리가 실패하는 경우 (부분 실패)") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        val room = Room.createGroup("부분 실패 테스트 방")
        val failingGuest = RoomParticipant.reconstitute(
            room = room,
            userId = 6L,
            joinedAt = ZonedDateTime.now().minusDays(10),
            participantType = com.sportsapp.domain.message.vo.ParticipantType.GUEST,
            canSpeak = true,
            expiresAt = ZonedDateTime.now().minusDays(1),
            lastReadMessageId = null,
        )
        val succeedingGuest = RoomParticipant.reconstitute(
            room = room,
            userId = 7L,
            joinedAt = ZonedDateTime.now().minusDays(10),
            participantType = com.sportsapp.domain.message.vo.ParticipantType.GUEST,
            canSpeak = true,
            expiresAt = ZonedDateTime.now().minusDays(1),
            lastReadMessageId = null,
        )

        every { roomParticipantRepository.findExpiredGuestsBefore(any()) } returns listOf(failingGuest, succeedingGuest)
        every { roomParticipantRepository.save(failingGuest) } throws RuntimeException("db error")
        every { roomParticipantRepository.save(succeedingGuest) } returns succeedingGuest

        When("evictExpired 를 호출하면") {
            val evictedCount = service.evictExpired()

            Then("실패한 1건은 로깅 후 건너뛰고 나머지 1건은 방출되어 evictedCount 는 1이다") {
                evictedCount shouldBe 1
                verify { roomParticipantRepository.save(succeedingGuest) }
            }
        }
    }

    Given("방의 정회원(MEMBER)이 게스트를 수동 방출하는 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        val room = Room.createGroup("수동 방출 테스트 방")
        val host = RoomParticipant.create(room, 1L)
        val guest = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 1L) } returns host
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 5L) } returns guest
        every { roomParticipantRepository.save(guest) } returns guest

        When("evict(roomId=1, userId=5, requesterId=1) 를 호출하면") {
            val evicted = service.evict(roomId = 1L, userId = 5L, requesterId = 1L)

            Then("게스트가 즉시 방출(soft-delete)된다") {
                evicted.isDeleted shouldBe true
            }
        }
    }

    Given("정회원이 아닌 사용자가 수동 방출을 시도하는 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        val room = Room.createGroup("방장 검증 테스트 방")
        val otherGuest = RoomParticipant.forGuest(room = room, userId = 2L, canSpeak = true, expiresInDays = 7L)
        val guest = RoomParticipant.forGuest(room = room, userId = 5L, canSpeak = true, expiresInDays = 7L)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 2L) } returns otherGuest
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 5L) } returns guest

        When("evict(roomId=1, userId=5, requesterId=2) 를 호출하면") {
            Then("NotRoomHostException 이 발생한다") {
                shouldThrow<NotRoomHostException> {
                    service.evict(roomId = 1L, userId = 5L, requesterId = 2L)
                }
            }
        }
    }

    Given("요청자가 방 참여자가 아닌 경우") {
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val service = GuestEvictionDomainService(roomParticipantRepository)

        every { roomParticipantRepository.findActiveByRoomIdAndUserId(1L, 99L) } returns null

        When("evict(roomId=1, userId=5, requesterId=99) 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    service.evict(roomId = 1L, userId = 5L, requesterId = 99L)
                }
            }
        }
    }
})
