package com.sportsapp.presentation.message.dto.response

import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomListView
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class RoomResponseTest : BehaviorSpec({

    Given("마지막 메시지가 50자를 초과하는 RoomListView") {
        val lastMessageAt = ZonedDateTime.now()
        val longContent = "가".repeat(80)
        val view = RoomListView(
            roomId = 1L,
            type = RoomType.GROUP,
            name = "축구 모임",
            contextType = RoomContextType.COMMUNITY,
            lastMessageContent = longContent,
            lastMessageAt = lastMessageAt,
        )

        When("RoomResponse.of(RoomListView) 로 변환하면") {
            val response = RoomResponse.of(view)

            Then("lastMessagePreview 는 최대 50자로 잘린다") {
                response.id shouldBe 1L
                response.type shouldBe RoomType.GROUP
                response.name shouldBe "축구 모임"
                response.contextType shouldBe RoomContextType.COMMUNITY
                response.lastMessagePreview?.length shouldBe 50
                response.lastMessagePreview shouldBe longContent.take(50)
                response.lastMessageAt shouldBe lastMessageAt
            }
        }
    }

    Given("마지막 메시지가 없는 RoomListView") {
        val view = RoomListView(
            roomId = 2L,
            type = RoomType.DIRECT,
            name = null,
            contextType = null,
            lastMessageContent = null,
            lastMessageAt = null,
        )

        When("RoomResponse.of(RoomListView) 로 변환하면") {
            val response = RoomResponse.of(view)

            Then("lastMessagePreview·lastMessageAt·contextType 이 모두 null 이다") {
                response.lastMessagePreview.shouldBeNull()
                response.lastMessageAt.shouldBeNull()
                response.contextType.shouldBeNull()
            }
        }
    }

    Given("50자 이하 짧은 마지막 메시지의 RoomListView") {
        val view = RoomListView(
            roomId = 3L,
            type = RoomType.GROUP,
            name = "짧은 방",
            contextType = null,
            lastMessageContent = "안녕하세요",
            lastMessageAt = ZonedDateTime.now(),
        )

        When("RoomResponse.of(RoomListView) 로 변환하면") {
            val response = RoomResponse.of(view)

            Then("원문 그대로 lastMessagePreview 에 담긴다") {
                response.lastMessagePreview shouldBe "안녕하세요"
            }
        }
    }

    Given("컨텍스트 없이 저장된 기존 Room 엔티티") {
        val room = Room.createGroup("농구 모임")

        When("RoomResponse.of(Room) 으로 변환하면") {
            val response = RoomResponse.of(room)

            Then("contextType 은 null, lastMessagePreview 도 null 이다 (Room 은 메시지 원문을 보유하지 않음)") {
                response.contextType.shouldBeNull()
                response.lastMessagePreview.shouldBeNull()
            }
        }
    }

    Given("컨텍스트가 있는 Room 엔티티") {
        val contextRoom = Room.createForContext(RoomType.GROUP, RoomContextType.GOODS_PRODUCT, 99L, "중고거래방")

        When("RoomResponse.of(Room) 으로 변환하면") {
            val response = RoomResponse.of(contextRoom)

            Then("contextType 이 그대로 보존된다") {
                response.contextType.shouldNotBeNull()
                response.contextType shouldBe RoomContextType.GOODS_PRODUCT
            }
        }
    }
})
