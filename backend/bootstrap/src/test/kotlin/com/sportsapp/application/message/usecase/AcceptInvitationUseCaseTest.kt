package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AcceptInvitationUseCaseTest : BehaviorSpec({

    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val acceptInvitationUseCase = AcceptInvitationUseCase(guestInvitationDomainService)

    Given("초대 대상 본인이 초대를 수락하는 상황") {
        val room = Room.createGroup("축구 모임")
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        invitation.accept()
        every { guestInvitationDomainService.accept(invitationId = 5L, userId = 2L) } returns invitation

        When("execute(invitationId=5, userId=2) 를 호출하면") {
            val result = acceptInvitationUseCase.execute(invitationId = 5L, userId = 2L)

            Then("GuestInvitationDomainService.accept 결과가 그대로 반환된다") {
                result shouldBe invitation
                verify(exactly = 1) { guestInvitationDomainService.accept(invitationId = 5L, userId = 2L) }
            }
        }
    }
})
