package com.sportsapp.domain.message.event

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import java.time.ZonedDateTime

class MessageSentEventTest : BehaviorSpec({

    Given("MessageSentEvent 를 생성하면") {
        val sentAt = ZonedDateTime.now()

        When("messageId 를 aggregateId 로 전달하면") {
            val event = MessageSentEvent(
                messageId = 1L,
                roomId = 10L,
                senderId = 100L,
                content = "안녕하세요",
                sentAt = sentAt,
            )

            Then("aggregateId 가 messageId 와 같고 topic 은 null(in-process 전용) 이다") {
                event.aggregateId shouldBe 1L
                event.roomId shouldBe 10L
                event.senderId shouldBe 100L
                event.content shouldBe "안녕하세요"
                event.sentAt shouldBe sentAt
                event.topic.shouldBeNull()
                event.eventId.shouldNotBeBlank()
            }
        }
    }
})
