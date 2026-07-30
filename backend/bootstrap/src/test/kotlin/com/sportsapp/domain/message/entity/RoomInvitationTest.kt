package com.sportsapp.domain.message.entity

import com.sportsapp.domain.message.exception.InvitationNotTransitionableException
import com.sportsapp.domain.message.exception.NotInvitationTargetException
import com.sportsapp.domain.message.vo.InvitationStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class RoomInvitationTest : BehaviorSpec({

    Given("RoomInvitation.create(room, inviter, invitee, canSpeak, expiresInDays) 호출") {
        val room = Room.createGroup("축구 모임")
        val before = ZonedDateTime.now()

        When("발화 가능 게스트로 7일 만료 초대를 생성하면") {
            val invitation = RoomInvitation.create(
                room = room,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                canSpeak = true,
                expiresInDays = 7L,
            )
            val after = ZonedDateTime.now()

            Then("상태는 PENDING 이고 canSpeak·expiresAt(now+7d)이 설정된다") {
                invitation.currentStatus shouldBe InvitationStatus.PENDING
                invitation.canSpeak shouldBe true
                invitation.inviterUserId shouldBe 1L
                invitation.inviteeUserId shouldBe 2L
                invitation.currentRespondedAt.shouldBeNull()
                (invitation.expiresAt.isAfter(before.plusDays(7)) || invitation.expiresAt.isEqual(before.plusDays(7))) shouldBe true
                (invitation.expiresAt.isBefore(after.plusDays(7)) || invitation.expiresAt.isEqual(after.plusDays(7))) shouldBe true
            }
        }

        When("expiresInDays 가 0 이하이면") {
            Then("IllegalArgumentException 이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    RoomInvitation.create(room, 1L, 2L, true, 0L)
                }
            }
        }
    }

    Given("PENDING 상태의 초대") {
        val room = Room.createGroup("축구 모임")

        When("accept() 를 호출하면") {
            val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
            invitation.accept()

            Then("상태가 ACCEPTED 로 전이되고 respondedAt 이 기록된다") {
                invitation.currentStatus shouldBe InvitationStatus.ACCEPTED
                invitation.currentRespondedAt.shouldNotBeNull()
            }
        }

        When("reject() 를 호출하면") {
            val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
            invitation.reject()

            Then("상태가 REJECTED 로 전이된다") {
                invitation.currentStatus shouldBe InvitationStatus.REJECTED
            }
        }

        When("revoke() 를 호출하면") {
            val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
            invitation.revoke()

            Then("상태가 REVOKED 로 전이된다") {
                invitation.currentStatus shouldBe InvitationStatus.REVOKED
            }
        }

        When("expire() 를 호출하면") {
            val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
            invitation.expire()

            Then("상태가 EXPIRED 로 전이된다") {
                invitation.currentStatus shouldBe InvitationStatus.EXPIRED
            }
        }
    }

    Given("이미 ACCEPTED 로 종료된 초대") {
        val room = Room.createGroup("축구 모임")
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        invitation.accept()

        When("다시 accept() 를 호출하면") {
            Then("InvitationNotTransitionableException 이 발생한다 (terminal 재전이 거부)") {
                shouldThrow<InvitationNotTransitionableException> {
                    invitation.accept()
                }
            }
        }

        When("reject() 를 호출하면") {
            Then("InvitationNotTransitionableException 이 발생한다") {
                shouldThrow<InvitationNotTransitionableException> {
                    invitation.reject()
                }
            }
        }
    }

    Given("초대 대상(invitee)이 2L 인 초대") {
        val room = Room.createGroup("축구 모임")
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)

        When("초대 대상 본인이 validateInvitee(2L) 를 호출하면") {
            Then("예외 없이 통과한다") {
                invitation.validateInvitee(2L)
            }
        }

        When("초대 대상이 아닌 사용자가 validateInvitee(99L) 를 호출하면") {
            Then("NotInvitationTargetException 이 발생한다") {
                shouldThrow<NotInvitationTargetException> {
                    invitation.validateInvitee(99L)
                }
            }
        }
    }

    Given("RoomInvitation.reconstitute(...) 로 영속화 계층에서 복원") {
        val room = Room.createGroup("축구 모임")
        val expiresAt = ZonedDateTime.now().plusDays(3)
        val respondedAt = ZonedDateTime.now()

        When("ACCEPTED 상태로 복원하면") {
            val invitation = RoomInvitation.reconstitute(
                room = room,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                status = InvitationStatus.ACCEPTED,
                canSpeak = false,
                expiresAt = expiresAt,
                respondedAt = respondedAt,
            )

            Then("필드가 검증 없이 그대로 복구된다") {
                invitation.currentStatus shouldBe InvitationStatus.ACCEPTED
                invitation.canSpeak shouldBe false
                invitation.expiresAt shouldBe expiresAt
                invitation.currentRespondedAt shouldBe respondedAt
            }
        }
    }
})
