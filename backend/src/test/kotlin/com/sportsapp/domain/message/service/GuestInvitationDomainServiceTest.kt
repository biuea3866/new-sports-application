package com.sportsapp.domain.message.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.entity.RoomParticipant
import com.sportsapp.domain.message.exception.InvitationNotTransitionableException
import com.sportsapp.domain.message.exception.NotInvitationTargetException
import com.sportsapp.domain.message.exception.NotRoomHostException
import com.sportsapp.domain.message.repository.RoomInvitationRepository
import com.sportsapp.domain.message.repository.RoomParticipantRepository
import com.sportsapp.domain.message.repository.RoomRepository
import com.sportsapp.domain.message.vo.InvitationStatus
import com.sportsapp.domain.message.vo.ParticipantType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * mock/서비스는 [Given] 블록마다 새로 만든다 — [GuestEvictionDomainServiceTest]와 동일한 관례.
 * Kotest `BehaviorSpec`은 기본적으로 spec 인스턴스를 재사용하므로, 최상위에서 mock을 한 번만
 * 만들면 이전 `Given`의 호출 카운트가 다음 `Given`의 `verify(exactly = N)`에 누적되어 leak된다.
 *
 * 방장 판정은 `rooms.host_user_id`(BE-13)를 [Room.requireHostedBy]로 위임한다 — 방을
 * `Room.createGroup(name, hostUserId = ...)`으로 만들어 host를 명시적으로 지정한다. `invite`는
 * host 검증을 위해 항상 방을 먼저 조회하므로(멱등 반환 경로 포함), 과거처럼 "PENDING 초대가 있으면
 * roomRepository 를 아예 조회하지 않는다"는 최적화는 더 이상 성립하지 않는다.
 */
class GuestInvitationDomainServiceTest : BehaviorSpec({

    Given("방장(1L)이 비멤버 정회원(2L)을 초대하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        every { roomRepository.findById(10L) } returns room
        every { invitationRepository.findPendingBy(10L, 2L) } returns null
        val savedSlot = slot<RoomInvitation>()
        every { invitationRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        When("invite(roomId=10, inviter=1, invitee=2, canSpeak=true, expiresInDays=7) 를 호출하면") {
            val result = guestInvitationDomainService.invite(
                roomId = 10L,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                canSpeak = true,
                expiresInDays = 7L,
            )

            Then("PENDING 초대가 생성되어 저장된다") {
                result.currentStatus shouldBe InvitationStatus.PENDING
                result.inviterUserId shouldBe 1L
                result.inviteeUserId shouldBe 2L
                verify(exactly = 1) { invitationRepository.save(any()) }
            }
        }
    }

    Given("동일 (room, invitee) 에 이미 PENDING 초대가 존재하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val existingInvitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { roomRepository.findById(10L) } returns room
        every { invitationRepository.findPendingBy(10L, 2L) } returns existingInvitation

        When("동일 조건으로 다시 invite 를 호출하면") {
            val result = guestInvitationDomainService.invite(
                roomId = 10L,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                canSpeak = true,
                expiresInDays = 7L,
            )

            Then("기존 초대가 그대로 반환되고 신규 저장은 발생하지 않는다 (멱등)") {
                result shouldBe existingInvitation
                verify(exactly = 0) { invitationRepository.save(any()) }
            }

            Then("host 검증을 위해 방은 정확히 1회 조회된다") {
                verify(exactly = 1) { roomRepository.findById(10L) }
            }
        }
    }

    Given("방장이 아닌 사용자(99L)가 초대를 시도하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        every { roomRepository.findById(10L) } returns room

        When("invite(roomId=10, inviter=99, invitee=2, ...) 를 호출하면") {
            Then("NotRoomHostException 이 발생한다") {
                shouldThrow<NotRoomHostException> {
                    guestInvitationDomainService.invite(
                        roomId = 10L,
                        inviterUserId = 99L,
                        inviteeUserId = 2L,
                        canSpeak = true,
                        expiresInDays = 7L,
                    )
                }
                verify(exactly = 0) { invitationRepository.findPendingBy(any(), any()) }
            }
        }
    }

    Given("PENDING 초대(id=5)를 초대 대상(2L)이 수락하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { invitationRepository.findById(5L) } returns invitation
        every { invitationRepository.save(any()) } answers { firstArg() }
        val participantSlot = slot<RoomParticipant>()
        every { roomParticipantRepository.save(capture(participantSlot)) } answers { participantSlot.captured }

        When("accept(invitationId=5, userId=2) 를 호출하면") {
            val result = guestInvitationDomainService.accept(invitationId = 5L, userId = 2L)

            Then("초대는 ACCEPTED 로 전이되고 게스트 참여자가 canSpeak·expiresAt 을 가지고 추가된다") {
                result.currentStatus shouldBe InvitationStatus.ACCEPTED
                participantSlot.captured.participantType shouldBe ParticipantType.GUEST
                participantSlot.captured.canSpeak shouldBe true
                val grantedExpiresAt = requireNotNull(participantSlot.captured.expiresAt)
                grantedExpiresAt.isAfter(ZonedDateTime.now()) shouldBe true
                verify(exactly = 1) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("PENDING 초대(id=6)를 초대 대상이 아닌 사용자(999L)가 수락하려는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { invitationRepository.findById(6L) } returns invitation

        When("accept(invitationId=6, userId=999) 를 호출하면") {
            Then("NotInvitationTargetException 이 발생한다") {
                shouldThrow<NotInvitationTargetException> {
                    guestInvitationDomainService.accept(invitationId = 6L, userId = 999L)
                }
            }
        }
    }

    Given("이미 ACCEPTED 로 종료된 초대(id=7)") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        invitation.accept()
        every { invitationRepository.findById(7L) } returns invitation

        When("다시 accept(invitationId=7, userId=2) 를 호출하면") {
            Then("InvitationNotTransitionableException 이 발생한다 (terminal 보호)") {
                shouldThrow<InvitationNotTransitionableException> {
                    guestInvitationDomainService.accept(invitationId = 7L, userId = 2L)
                }
            }
        }
    }

    Given("PENDING 초대(id=8)를 초대 대상(2L)이 거절하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { invitationRepository.findById(8L) } returns invitation
        every { invitationRepository.save(any()) } answers { firstArg() }

        When("reject(invitationId=8, userId=2) 를 호출하면") {
            val result = guestInvitationDomainService.reject(invitationId = 8L, userId = 2L)

            Then("초대는 REJECTED 가 되고 참여자 추가는 발생하지 않는다") {
                result.currentStatus shouldBe InvitationStatus.REJECTED
                verify(exactly = 0) { roomParticipantRepository.save(any()) }
            }
        }
    }

    Given("PENDING 초대(id=9)를 방장(1L)이 철회하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { invitationRepository.findById(9L) } returns invitation
        every { invitationRepository.save(any()) } answers { firstArg() }

        When("revoke(invitationId=9, hostUserId=1) 를 호출하면") {
            val result = guestInvitationDomainService.revoke(invitationId = 9L, hostUserId = 1L)

            Then("초대는 REVOKED 가 된다") {
                result.currentStatus shouldBe InvitationStatus.REVOKED
            }
        }
    }

    Given("PENDING 초대(id=11)를 방장이 아닌 사용자(99L)가 철회하려는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        every { invitationRepository.findById(11L) } returns invitation

        When("revoke(invitationId=11, hostUserId=99) 를 호출하면") {
            Then("NotRoomHostException 이 발생한다") {
                shouldThrow<NotRoomHostException> {
                    guestInvitationDomainService.revoke(invitationId = 11L, hostUserId = 99L)
                }
            }
        }
    }

    Given("사용자(2L)가 받은 PENDING 초대 2건이 존재하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        val room = Room.createGroup("축구 모임", hostUserId = 1L)
        val pendingInvitations = listOf(
            RoomInvitation.create(room, 1L, 2L, true, 7L),
            RoomInvitation.create(room, 3L, 2L, false, 3L),
        )
        every { invitationRepository.findPendingByInvitee(2L) } returns pendingInvitations

        When("findMyPendingInvitations(2L) 을 호출하면") {
            val result = guestInvitationDomainService.findMyPendingInvitations(2L)

            Then("PENDING 초대 2건이 반환된다") {
                result shouldHaveSize 2
            }
        }
    }

    Given("존재하지 않는 초대(id=404)") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        every { invitationRepository.findById(404L) } returns null

        When("accept(invitationId=404, userId=1) 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    guestInvitationDomainService.accept(invitationId = 404L, userId = 1L)
                }
            }
        }
    }

    Given("존재하지 않는 방(id=999)에 초대를 시도하는 상황") {
        val roomRepository = mockk<RoomRepository>()
        val roomParticipantRepository = mockk<RoomParticipantRepository>()
        val invitationRepository = mockk<RoomInvitationRepository>()
        val guestInvitationDomainService = GuestInvitationDomainService(
            roomRepository = roomRepository,
            roomParticipantRepository = roomParticipantRepository,
            invitationRepository = invitationRepository,
        )
        every { roomRepository.findById(999L) } returns null

        When("invite(roomId=999, inviter=1, invitee=2, ...) 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    guestInvitationDomainService.invite(
                        roomId = 999L,
                        inviterUserId = 1L,
                        inviteeUserId = 2L,
                        canSpeak = true,
                        expiresInDays = 7L,
                    )
                }
            }
        }
    }
})
