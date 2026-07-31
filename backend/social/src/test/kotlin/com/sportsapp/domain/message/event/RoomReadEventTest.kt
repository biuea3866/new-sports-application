package com.sportsapp.domain.message.event

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

class RoomReadEventTest : BehaviorSpec({

    Given("RoomReadEvent 를 생성하면") {
        When("roomId 를 aggregateId 로 전달하면") {
            val event = RoomReadEvent(
                roomId = 10L,
                userId = 100L,
                lastReadMessageId = 50L,
            )

            Then("aggregateId 가 roomId 와 같고 topic 은 null(in-process 전용) 이다") {
                event.aggregateId shouldBe 10L
                event.roomId shouldBe 10L
                event.userId shouldBe 100L
                event.lastReadMessageId shouldBe 50L
                event.topic.shouldBeNull()
                event.eventId.shouldNotBeBlank()
            }
        }
    }
})
