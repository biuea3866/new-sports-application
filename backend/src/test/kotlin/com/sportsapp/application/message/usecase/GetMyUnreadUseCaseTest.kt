package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.ReadCursorDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetMyUnreadUseCaseTest : BehaviorSpec({

    val readCursorDomainService = mockk<ReadCursorDomainService>()
    val getMyUnreadUseCase = GetMyUnreadUseCase(readCursorDomainService)

    Given("내가 참여 중인 방이 여러 개일 때") {
        every { readCursorDomainService.unreadForMyRooms(10L) } returns mapOf(1L to 3L, 2L to 0L)

        When("execute(userId=10) 를 호출하면") {
            val result = getMyUnreadUseCase.execute(10L)

            Then("방별 RoomUnreadResponse 리스트를 반환한다") {
                result shouldHaveSize 2
                result.first { it.roomId == 1L }.unreadCount shouldBe 3L
                result.first { it.roomId == 2L }.unreadCount shouldBe 0L
            }
        }
    }

    Given("참여 중인 방이 없을 때") {
        every { readCursorDomainService.unreadForMyRooms(20L) } returns emptyMap()

        When("execute(userId=20) 를 호출하면") {
            val result = getMyUnreadUseCase.execute(20L)

            Then("빈 리스트를 반환한다 (빈 상태)") {
                result.shouldBeEmpty()
            }
        }
    }
})
