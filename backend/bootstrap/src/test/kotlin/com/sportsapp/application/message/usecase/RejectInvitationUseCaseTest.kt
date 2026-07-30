package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class RejectInvitationUseCaseTest : BehaviorSpec({

    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val rejectInvitationUseCase = RejectInvitationUseCase(guestInvitationDomainService)

    Given("초대 대상 본인이 초대를 거절하는 상황") {
        val room = Room.createGroup("축구 모임")
        val invitation = RoomInvitation.create(room, 1L, 2L, true, 7L)
        invitation.reject()
        every { guestInvitationDomainService.reject(invitationId = 8L, userId = 2L) } returns invitation

        When("execute(invitationId=8, userId=2) 를 호출하면") {
            val result = rejectInvitationUseCase.execute(invitationId = 8L, userId = 2L)

            Then("GuestInvitationDomainService.reject 결과가 그대로 반환된다") {
                result shouldBe invitation
                verify(exactly = 1) { guestInvitationDomainService.reject(invitationId = 8L, userId = 2L) }
            }
        }
    }
})
