package com.sportsapp.domain.booking.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BookingEventTest : BehaviorSpec({

    val kafkaObjectMapper: ObjectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    Given("BookingEvent.Confirmed 인스턴스") {
        val event = BookingEvent.Confirmed(bookingId = 12L, paymentId = 55L, recipientUserId = 900L)

        When("topic 과 필드를 확인하면") {
            Then("서브 도메인 단일 토픽과 판별자/수신자를 노출한다") {
                event.topic shouldBe "event.booking.booking.v1"
                event.aggregateId shouldBe 12L
                event.bookingId shouldBe 12L
                event.paymentId shouldBe 55L
                event.recipientUserId shouldBe 900L
                event.eventType shouldBe "CONFIRMED"
            }
        }
    }

    Given("BookingEvent.Confirmed 를 JSON 으로 직렬화한 뒤 sealed 베이스로 역직렬화하면") {
        val original = BookingEvent.Confirmed(bookingId = 33L, paymentId = 77L, recipientUserId = 901L)
        val json = kafkaObjectMapper.writeValueAsString(original)

        When("eventType 판별자로 하위 타입을 결정하면") {
            val restored = kafkaObjectMapper.readValue(json, BookingEvent::class.java)

            Then("Confirmed 로 복원되고 필드가 보존된다") {
                restored.shouldBeInstanceOf<BookingEvent.Confirmed>()
                restored.bookingId shouldBe 33L
                restored.paymentId shouldBe 77L
                restored.recipientUserId shouldBe 901L
                restored.eventId shouldBe original.eventId
            }
        }
    }
})
