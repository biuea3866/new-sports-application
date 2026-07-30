package com.sportsapp.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-01] ZonedDateTime 필드가 ISO 8601 문자열로 직렬화된다
 */
class JsonSerializerTest : BehaviorSpec({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    data class TestEvent(val occurredAt: ZonedDateTime, val name: String)

    Given("ZonedDateTime 필드를 가진 DTO") {
        val event = TestEvent(
            occurredAt = ZonedDateTime.of(2026, 5, 19, 12, 0, 0, 0, ZoneOffset.UTC),
            name = "test-event",
        )

        When("ObjectMapper로 직렬화하면") {
            val json = objectMapper.writeValueAsString(event)

            Then("[U-01] occurredAt 이 ISO 8601 형식으로 직렬화된다") {
                json shouldContain "2026-05-19T12:00:00Z"
            }

            Then("[U-01] 숫자 타임스탬프가 아닌 문자열로 직렬화된다") {
                val tree = objectMapper.readTree(json)
                tree.get("occurredAt").isTextual shouldBe true
            }
        }
    }
})
