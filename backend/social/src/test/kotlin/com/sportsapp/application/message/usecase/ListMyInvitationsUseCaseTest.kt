package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.entity.RoomInvitation
import com.sportsapp.domain.message.service.GuestInvitationDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk

class ListMyInvitationsUseCaseTest : BehaviorSpec({

    val guestInvitationDomainService = mockk<GuestInvitationDomainService>()
    val listMyInvitationsUseCase = ListMyInvitationsUseCase(guestInvitationDomainService)

    Given("사용자가 받은 PENDING 초대가 2건 존재하는 상황") {
        val room = Room.createGroup("축구 모임")
        val pendingInvitations = listOf(
            RoomInvitation.create(room, 1L, 2L, true, 7L),
            RoomInvitation.create(room, 3L, 2L, false, 3L),
        )
        every { guestInvitationDomainService.findMyPendingInvitations(2L) } returns pendingInvitations

        When("execute(userId=2) 를 호출하면") {
            val result = listMyInvitationsUseCase.execute(2L)

            Then("PENDING 초대 목록이 그대로 반환된다") {
                result shouldHaveSize 2
            }
        }
    }
})
