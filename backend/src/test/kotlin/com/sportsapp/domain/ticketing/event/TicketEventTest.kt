package com.sportsapp.domain.ticketing.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class TicketEventTest : BehaviorSpec({

    val kafkaObjectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    Given("TicketEvent.Issued 인스턴스") {
        val event = TicketEvent.Issued(ticketOrderId = 5L, recipientUserId = 700L, eventTitle = "월드컵 결승")

        When("topic 과 필드를 확인하면") {
            Then("서브 도메인 단일 토픽과 판별자/수신자/이벤트 제목을 노출한다") {
                event.topic shouldBe "event.ticketing.ticket.v1"
                event.aggregateId shouldBe 5L
                event.ticketOrderId shouldBe 5L
                event.recipientUserId shouldBe 700L
                event.eventTitle shouldBe "월드컵 결승"
                event.eventType shouldBe "ISSUED"
            }
        }
    }

    Given("TicketEvent.Issued 를 JSON 으로 직렬화한 뒤 sealed 베이스로 역직렬화하면") {
        val original = TicketEvent.Issued(ticketOrderId = 9L, recipientUserId = 701L, eventTitle = "정규 리그")
        val json = kafkaObjectMapper.writeValueAsString(original)

        When("eventType 판별자로 하위 타입을 결정하면") {
            val restored = kafkaObjectMapper.readValue(json, TicketEvent::class.java)

            Then("Issued 로 복원되고 필드가 보존된다") {
                restored.shouldBeInstanceOf<TicketEvent.Issued>()
                restored.ticketOrderId shouldBe 9L
                restored.recipientUserId shouldBe 701L
                restored.eventTitle shouldBe "정규 리그"
                restored.eventId shouldBe original.eventId
            }
        }
    }
})
