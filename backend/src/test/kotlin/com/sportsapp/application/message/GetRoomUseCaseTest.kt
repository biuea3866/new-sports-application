package com.sportsapp.application.message

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetRoomUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val getRoomUseCase = GetRoomUseCase(messageDomainService)

    Given("존재하는 Room") {
        val room = Room(type = RoomType.GROUP, name = "그룹 룸")
        every { messageDomainService.getRoom(10L) } returns room

        When("roomId=10 으로 execute 를 호출하면") {
            val result = getRoomUseCase.execute(10L)

            Then("Room 정보가 반환된다") {
                result.type shouldBe RoomType.GROUP
                result.name shouldBe "그룹 룸"
            }
        }
    }

    Given("존재하지 않는 Room") {
        every { messageDomainService.getRoom(999L) } throws ResourceNotFoundException("Room", 999L)

        When("roomId=999 로 execute 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    getRoomUseCase.execute(999L)
                }
            }
        }
    }
})
