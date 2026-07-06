package com.sportsapp.application.message.usecase

import com.sportsapp.application.message.dto.ProvisionContextRoomCommand
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomContextDomainService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ProvisionContextRoomUseCaseTest : BehaviorSpec({

    val roomContextDomainService = mockk<RoomContextDomainService>()
    val useCase = ProvisionContextRoomUseCase(roomContextDomainService)

    Given("커뮤니티 개설에 대응하는 컨텍스트 방 provision 커맨드") {
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 10L, "주말 축구 모임")
        every {
            roomContextDomainService.provision(
                contextType = RoomContextType.COMMUNITY,
                contextId = 10L,
                name = "주말 축구 모임",
                hostUserId = 1L,
            )
        } returns room

        When("execute 를 호출하면") {
            val command = ProvisionContextRoomCommand(
                contextType = RoomContextType.COMMUNITY,
                contextId = 10L,
                name = "주말 축구 모임",
                hostUserId = 1L,
            )
            val result = useCase.execute(command)

            Then("RoomContextDomainService.provision 이 위임 호출되고 결과 방이 반환된다") {
                result shouldBe room
                verify(exactly = 1) {
                    roomContextDomainService.provision(
                        contextType = RoomContextType.COMMUNITY,
                        contextId = 10L,
                        name = "주말 축구 모임",
                        hostUserId = 1L,
                    )
                }
            }
        }
    }
})
