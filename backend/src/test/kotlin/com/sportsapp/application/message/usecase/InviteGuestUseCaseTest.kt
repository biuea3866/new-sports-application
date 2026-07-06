package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.InviteGuestCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import com.sportsapp.domain.message.vo.InvitationResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class InviteGuestUseCaseTest : BehaviorSpec({

    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val inviteGuestUseCase = InviteGuestUseCase(guestInvitationDomainService)

    Given("방장이 게스트를 초대하는 커맨드") {
        val room = Room.createGroup("축구 모임")
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        val invitationResult = InvitationResult(invitation = invitation, reused = false)
        val command = InviteGuestCommand(
            roomId = 10L,
            inviterUserId = 1L,
            inviteeUserId = 2L,
            canSpeak = true,
            expiresInDays = 7L,
        )
        every {
            guestInvitationDomainService.invite(
                roomId = 10L,
                inviterUserId = 1L,
                inviteeUserId = 2L,
                canSpeak = true,
                expiresInDays = 7L,
            )
        } returns invitationResult

        When("execute 를 호출하면") {
            val result = inviteGuestUseCase.execute(command)

            Then("GuestInvitationDomainService.invite 결과가 그대로 반환된다") {
                result shouldBe invitationResult
                verify(exactly = 1) {
                    guestInvitationDomainService.invite(
                        roomId = 10L,
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
