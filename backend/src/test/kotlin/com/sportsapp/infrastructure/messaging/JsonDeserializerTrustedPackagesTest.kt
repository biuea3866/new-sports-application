package com.sportsapp.infrastructure.messaging

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.apache.kafka.common.header.internals.RecordHeaders
import org.springframework.kafka.support.serializer.JsonDeserializer

/**
 * [U-02] trusted.packages 외 패키지 DTO 역직렬화 요청은 거부된다
 * [U-03] Raw 타입 Kafka 레코드 빌드 차단은 harnessCheck Gradle task (no-consumer-record-raw) 가 처리한다.
 *        Consumer 는 DTO 직접 매핑 + JsonDeserializer 패턴만 허용.
 */
class JsonDeserializerTrustedPackagesTest : BehaviorSpec({

    val objectMapper = ObjectMapper().registerKotlinModule()

    data class TrustedEvent(val id: Long, val name: String)

    Given("JsonDeserializer 에 com.sportsapp.* 만 신뢰 패키지로 등록된 경우") {

        When("신뢰된 패키지 타입으로 역직렬화하면") {
            val deserializer = JsonDeserializer(TrustedEvent::class.java, objectMapper).apply {
                addTrustedPackages("com.sportsapp.*")
            }
            val bytes = objectMapper.writeValueAsBytes(TrustedEvent(id = 1L, name = "sports"))

            Then("[U-02] 역직렬화에 성공하여 null 이 아닌 결과를 반환한다") {
                val result = deserializer.deserialize("test.topic.v1", RecordHeaders(), bytes)
                result shouldNotBe null
            }
        }

        When("신뢰되지 않은 패키지 타입 헤더로 역직렬화를 시도하면") {
            val untrustedDeserializer = JsonDeserializer<Any>(objectMapper).apply {
                addTrustedPackages("com.sportsapp.*")
            }
            val bytes = objectMapper.writeValueAsBytes(mapOf("id" to 1, "name" to "hacked"))
            val headers = RecordHeaders().apply {
                add(
                    JsonDeserializer.VALUE_DEFAULT_TYPE,
                    "com.untrusted.evil.EvilPayload".toByteArray()
                )
            }

            Then("[U-02] Exception 이 발생하여 역직렬화를 거부한다") {
                shouldThrow<Exception> {
                    untrustedDeserializer.deserialize("test.topic.v1", headers, bytes)
                }.shouldBeInstanceOf<Exception>()
            }
        }
    }
})
