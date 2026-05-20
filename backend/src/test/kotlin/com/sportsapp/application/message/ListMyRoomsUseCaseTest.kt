package com.sportsapp.application.message

import com.sportsapp.domain.message.MessageDomainService
import com.sportsapp.domain.message.Room
import com.sportsapp.domain.message.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class ListMyRoomsUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val listMyRoomsUseCase = ListMyRoomsUseCase(messageDomainService)

    Given("userId=1 의 룸 2개가 존재") {
        val rooms = listOf(
            Room(type = RoomType.DIRECT, name = null),
            Room(type = RoomType.GROUP, name = "그룹"),
        )
        every { messageDomainService.findMyRooms(1L, null) } returns rooms

        When("keyword 없이 execute 를 호출하면") {
            val result = listMyRoomsUseCase.execute(userId = 1L, keyword = null)

            Then("2개 룸이 반환된다") {
                result shouldHaveSize 2
            }
        }
    }

    Given("keyword 검색") {
        val matchedRoom = Room(type = RoomType.GROUP, name = "축구 모임")
        every { messageDomainService.findMyRooms(1L, "축구") } returns listOf(matchedRoom)

        When("keyword='축구' 로 execute 를 호출하면") {
            val result = listMyRoomsUseCase.execute(userId = 1L, keyword = "축구")

            Then("매칭된 룸만 반환된다") {
                result shouldHaveSize 1
                result[0].name shouldBe "축구 모임"
            }
        }
    }
})
