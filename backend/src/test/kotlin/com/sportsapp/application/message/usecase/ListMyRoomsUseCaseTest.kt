package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.MessageDomainService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListMyRoomsUseCaseTest : BehaviorSpec({

    val messageDomainService = mockk<MessageDomainService>()
    val listMyRoomsUseCase = ListMyRoomsUseCase(messageDomainService)

    Given("userId=1 의 룸 2개 projection 이 존재") {
        val lastMessageAt = ZonedDateTime.now()
        val views = listOf(
            RoomListView(
                roomId = 1L,
                type = RoomType.DIRECT,
                name = null,
                contextType = null,
                lastMessageContent = null,
                lastMessageAt = null,
            ),
            RoomListView(
                roomId = 2L,
                type = RoomType.GROUP,
                name = "그룹",
                contextType = null,
                lastMessageContent = "안녕",
                lastMessageAt = lastMessageAt,
            ),
        )
        every { messageDomainService.findMyRoomViews(1L, null) } returns views

        When("keyword 없이 execute 를 호출하면") {
            val result = listMyRoomsUseCase.execute(userId = 1L, keyword = null)

            Then("2개 룸 projection 이 반환된다") {
                result shouldHaveSize 2
                result[1].lastMessageContent shouldBe "안녕"
                result[1].lastMessageAt shouldBe lastMessageAt
                result[0].lastMessageContent.shouldBeNull()
            }
        }
    }

    Given("keyword 검색") {
        val matchedView = RoomListView(
            roomId = 3L,
            type = RoomType.GROUP,
            name = "축구 모임",
            contextType = RoomContextType.COMMUNITY,
            lastMessageContent = "다음 경기 몇시?",
            lastMessageAt = ZonedDateTime.now(),
        )
        every { messageDomainService.findMyRoomViews(1L, "축구") } returns listOf(matchedView)

        When("keyword='축구' 로 execute 를 호출하면") {
            val result = listMyRoomsUseCase.execute(userId = 1L, keyword = "축구")

            Then("매칭된 룸만 반환되고 contextType 이 보존된다") {
                result shouldHaveSize 1
                result[0].name shouldBe "축구 모임"
                result[0].contextType.shouldNotBeNull()
                result[0].contextType shouldBe RoomContextType.COMMUNITY
            }
        }
    }
})
