package com.sportsapp.application.message

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.NotRoomParticipantException
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

    Given("존재하는 Room 의 참여자") {
        val room = Room(type = RoomType.GROUP, name = "그룹 룸")
        every { messageDomainService.getRoom(10L, 1L) } returns room

        When("roomId=10, userId=1 로 execute 를 호출하면") {
            val result = getRoomUseCase.execute(10L, 1L)

            Then("Room 정보가 반환된다") {
                result.type shouldBe RoomType.GROUP
                result.name shouldBe "그룹 룸"
            }
        }
    }

    Given("존재하지 않는 Room") {
        every { messageDomainService.getRoom(999L, 1L) } throws ResourceNotFoundException("Room", 999L)

        When("roomId=999 로 execute 를 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    getRoomUseCase.execute(999L, 1L)
                }
            }
        }
    }

    Given("존재하는 Room 의 비참여자") {
        every { messageDomainService.getRoom(10L, 99L) } throws NotRoomParticipantException(99L, 10L)

        When("roomId=10, userId=99(비참여자) 로 execute 를 호출하면") {
            Then("[U-05] NotRoomParticipantException 이 발생한다") {
                shouldThrow<NotRoomParticipantException> {
                    getRoomUseCase.execute(10L, 99L)
                }
            }
        }
    }
})
