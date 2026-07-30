package com.sportsapp.domain.message.service

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.repository.RoomInvitationRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * BE-13 회귀 방지 — `GuestInvitationDomainService.invite`(초대)와
 * `GuestEvictionDomainService.evict`(수동 방출)가 같은 방·같은 사용자에 대해 항상 동일한
 * 방장 판정 결과를 내는지 검증한다.
 *
 * 변경 전에는 초대는 "활성 MEMBER 중 joinedAt 최솟값 1명", 방출은 "활성 MEMBER 전원"을 방장으로
 * 간주해, 2번째로 참여한 MEMBER가 게스트 방출은 되는데 초대는 안 되는 비대칭이 있었다. `Room`이
 * `host_user_id`(BE-13)를 단일 소스로 노출한 뒤에는 두 서비스 모두 `Room.requireHostedBy`만
 * 사용하므로, 같은 사용자에 대해 둘 다 허용되거나 둘 다 거부되어야 한다.
 */
class RoomHostAuthorizationConsistencyTest : BehaviorSpec({

    Given("방장(1L)과 비-host MEMBER(2L)이 참여한 방") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val guestEvictionDomainService = GuestEvictionDomainService(roomRepository, roomParticipantRepository)

        val room = Room.createGroup("호스트 판정 일관성 테스트 방", hostUserId = 1L)
        val guestToEvict = RoomParticipant.forGuest(room = room, userId = 50L, canSpeak = true, expiresInDays = 7L)

        every { roomRepository.findById(100L) } returns room
        every { invitationRepository.findPendingBy(100L, 3L) } returns null
        every { roomParticipantRepository.findActiveByRoomIdAndUserId(100L, 50L) } returns guestToEvict
        every { roomParticipantRepository.save(guestToEvict) } returns guestToEvict

        When("비-host MEMBER(2L)가 초대와 방출을 각각 시도하면") {
            Then("초대(invite)도 NotRoomHostException 으로 거부된다") {
                shouldThrow<NotRoomHostException> {
                    guestInvitationDomainService.invite(
                        roomId = 100L,
                        inviterUserId = 2L,
                        inviteeUserId = 3L,
                        canSpeak = true,
                        expiresInDays = 7L,
                    )
                }
            }

            Then("방출(evict)도 NotRoomHostException 으로 거부된다 (과거 비대칭 제거)") {
                shouldThrow<NotRoomHostException> {
                    guestEvictionDomainService.evict(roomId = 100L, userId = 50L, requesterId = 2L)
                }
            }
        }

        When("방장(1L)이 초대와 방출을 각각 시도하면") {
            val savedInvitation = slot<RoomInvitation>()
            every { invitationRepository.save(capture(savedInvitation)) } answers { savedInvitation.captured }

            Then("초대(invite)가 허용된다") {
                shouldNotThrowAny {
                    guestInvitationDomainService.invite(
                        roomId = 100L,
                        inviterUserId = 1L,
                        inviteeUserId = 3L,
                        canSpeak = true,
                        expiresInDays = 7L,
                    )
                }
            }

            Then("방출(evict)도 허용된다") {
                shouldNotThrowAny {
                    guestEvictionDomainService.evict(roomId = 100L, userId = 50L, requesterId = 1L)
                }
            }
        }
    }
})
