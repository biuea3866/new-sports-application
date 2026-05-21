package com.sportsapp.infrastructure.persistence

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.sql.Timestamp
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [U-01] ZonedDateTimeAttributeConverter — UTC 기준 ZonedDateTime ↔ TIMESTAMP 변환이 라운드트립에서 instant를 보존한다
 */
class ZonedDateTimeAttributeConverterTest : BehaviorSpec({

    val converter = ZonedDateTimeAttributeConverter()

    Given("UTC ZonedDateTime") {
        val utcTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, java.time.ZoneOffset.UTC)

        When("convertToDatabaseColumn 호출") {
            val timestamp = converter.convertToDatabaseColumn(utcTime)

            Then("[U-01] Timestamp의 instant가 원본과 동일하다") {
                timestamp?.toInstant() shouldBe utcTime.toInstant()
            }
        }

        When("convertToDatabaseColumn 후 convertToEntityAttribute 라운드트립") {
            val timestamp = converter.convertToDatabaseColumn(utcTime)
            val restored = converter.convertToEntityAttribute(timestamp)

            Then("[U-01] 복원된 ZonedDateTime의 instant가 원본과 동일하다") {
                restored?.toInstant() shouldBe utcTime.toInstant()
            }
        }
    }

    Given("KST(Asia/Seoul) ZonedDateTime") {
        val kstTime = ZonedDateTime.of(2024, 6, 15, 21, 0, 0, 0, ZoneId.of("Asia/Seoul"))

        When("convertToDatabaseColumn 후 convertToEntityAttribute 라운드트립") {
            val timestamp = converter.convertToDatabaseColumn(kstTime)
            val restored = converter.convertToEntityAttribute(timestamp)

            Then("[U-01] 시간대가 다르더라도 instant가 보존된다") {
                restored?.toInstant() shouldBe kstTime.toInstant()
            }
        }
    }

    Given("null 값") {
        When("convertToDatabaseColumn(null) 호출") {
            val result = converter.convertToDatabaseColumn(null)
            Then("[U-01] null을 반환한다") { result.shouldBeNull() }
        }

        When("convertToEntityAttribute(null) 호출") {
            val result = converter.convertToEntityAttribute(null)
            Then("[U-01] null을 반환한다") { result.shouldBeNull() }
        }
    }
})
