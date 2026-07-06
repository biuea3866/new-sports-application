package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomParticipantException
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.ParticipantType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * "내 방 참여정보" 조회 (BE-14) — mock/서비스는 [Given]마다 새로 만든다
 * ([GuestInvitationDomainServiceTest]와 동일한 관례, spec 인스턴스 재사용으로 인한 leak 방지).
 */
class RoomParticipationQueryServiceTest : BehaviorSpec({

    Given("방장(1L) 본인이 정회원으로 참여 중인 방") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val roomParticipationQueryService = RoomParticipationQueryService(roomRepository, roomParticipantRepository)
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val participant = RoomParticipant.create(room, 1L)
        every { roomRepository.findById(10L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(10L, 1L) } returns participant

        When("getMyParticipation(roomId=10, userId=1) 을 호출하면") {
            val result = roomParticipationQueryService.getMyParticipation(roomId = 10L, userId = 1L)

            Then("MEMBER·canSpeak=true·expiresAt=null·isHost=true 가 반환된다") {
                result.roomId shouldBe room.id
                result.participantType shouldBe ParticipantType.MEMBER
                result.canSpeak shouldBe true
                result.expiresAt.shouldBeNull()
                result.isHost shouldBe true
            }
        }
    }

    Given("게스트(2L)가 발화 제한·만료 시각을 가지고 참여 중인 방") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val roomParticipationQueryService = RoomParticipationQueryService(roomRepository, roomParticipantRepository)
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val guest = RoomParticipant.forGuest(room, userId = 2L, canSpeak = false, expiresInDays = 7L)
        every { roomRepository.findById(10L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(10L, 2L) } returns guest

        When("getMyParticipation(roomId=10, userId=2) 를 호출하면") {
            val result = roomParticipationQueryService.getMyParticipation(roomId = 10L, userId = 2L)

            Then("GUEST·canSpeak=false·expiresAt 채워짐·isHost=false 가 반환된다") {
                result.participantType shouldBe ParticipantType.GUEST
                result.canSpeak shouldBe false
                result.expiresAt.shouldNotBeNull()
                result.isHost shouldBe false
            }
        }
    }

    Given("존재하지 않는 방(999L)") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val roomParticipationQueryService = RoomParticipationQueryService(roomRepository, roomParticipantRepository)
        every { roomRepository.findById(999L) } returns null

        When("getMyParticipation(roomId=999, userId=1) 을 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    roomParticipationQueryService.getMyParticipation(roomId = 999L, userId = 1L)
                }
            }
        }
    }

    Given("방(10L)에 참여하지 않은 사용자(99L)") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val roomParticipationQueryService = RoomParticipationQueryService(roomRepository, roomParticipantRepository)
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        every { roomRepository.findById(10L) } returns room
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(10L, 99L) } returns null

        When("getMyParticipation(roomId=10, userId=99) 를 호출하면") {
            Then("NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    roomParticipationQueryService.getMyParticipation(roomId = 10L, userId = 99L)
                }
            }
        }
    }
})
