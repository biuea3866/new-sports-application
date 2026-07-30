package com.sportsapp.domain.message.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class RoomListViewTest : BehaviorSpec({

    Given("마지막 메시지가 있는 방의 projection 값") {
        val lastMessageAt = ZonedDateTime.now()

        When("RoomListView 를 생성하면") {
            val view = RoomListView(
                roomId = 1L,
                type = RoomType.GROUP,
                name = "축구 모임",
                contextType = RoomContextType.COMMUNITY,
                lastMessageContent = "안녕하세요",
                lastMessageAt = lastMessageAt,
            )

            Then("모든 필드가 그대로 보존된다") {
                view.roomId shouldBe 1L
                view.type shouldBe RoomType.GROUP
                view.name shouldBe "축구 모임"
                view.contextType shouldBe RoomContextType.COMMUNITY
                view.lastMessageContent shouldBe "안녕하세요"
                view.lastMessageAt shouldBe lastMessageAt
            }
        }
    }

    Given("메시지가 없는 방의 projection 값") {
        When("lastMessageContent·lastMessageAt 없이 RoomListView 를 생성하면") {
            val view = RoomListView(
                roomId = 2L,
                type = RoomType.DIRECT,
                name = null,
                contextType = null,
                lastMessageContent = null,
                lastMessageAt = null,
            )

            Then("두 필드 모두 null 이다") {
                view.lastMessageContent.shouldBeNull()
                view.lastMessageAt.shouldBeNull()
            }
        }
    }
})
