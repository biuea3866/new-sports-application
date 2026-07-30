package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.LeaveContextRoomCommand
import com.sportsapp.domain.message.service.RoomContextDomainService
import com.sportsapp.domain.message.vo.RoomContextType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class LeaveContextRoomUseCaseTest : BehaviorSpec({

    val roomContextDomainService = mockk<RoomContextDomainService>()
    val useCase = LeaveContextRoomUseCase(roomContextDomainService)

    Given("커뮤니티 멤버 탈퇴/강퇴에 대응하는 컨텍스트 방 퇴장 커맨드") {
        every { roomContextDomainService.leaveContext(RoomContextType.COMMUNITY, 10L, 2L) } returns Unit

        When("execute 를 호출하면") {
            val command = LeaveContextRoomCommand(contextType = RoomContextType.COMMUNITY, contextId = 10L, userId = 2L)
            useCase.execute(command)

            Then("RoomContextDomainService.leaveContext 가 위임 호출된다") {
                verify(exactly = 1) { roomContextDomainService.leaveContext(RoomContextType.COMMUNITY, 10L, 2L) }
            }
        }
    }
})
