package com.sportsapp.infrastructure.persistence.mongo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * [U-01] ZonedDateTime ↔ Date 컨버터가 zone 정보 손실 없이 UTC instant를 보존한다
 */
class ZonedDateTimeConverterTest : BehaviorSpec({

    val writeConverter = ZonedDateTimeToDateConverter()
    val readConverter = DateToZonedDateTimeConverter()

    Given("UTC ZonedDateTime") {
        val utcTime = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)

        When("Date로 변환 후 ZonedDateTime으로 복원하면") {
            val date = writeConverter.convert(utcTime)
            val restored = readConverter.convert(date)

            Then("[U-01] instant가 원본과 동일하다") {
                restored.toInstant() shouldBe utcTime.toInstant()
            }

            Then("[U-01] 복원된 ZonedDateTime은 UTC zone이다") {
                restored.zone shouldBe ZoneOffset.UTC
            }
        }
    }

    Given("KST(Asia/Seoul) ZonedDateTime") {
        val kstTime = ZonedDateTime.of(2024, 6, 15, 21, 0, 0, 0, ZoneId.of("Asia/Seoul"))

        When("Date로 변환 후 ZonedDateTime으로 복원하면") {
            val date = writeConverter.convert(kstTime)
            val restored = readConverter.convert(date)

            Then("[U-01] zone이 다르더라도 instant가 보존된다") {
                restored.toInstant() shouldBe kstTime.toInstant()
            }
        }
    }

    Given("나노초가 포함된 ZonedDateTime") {
        val preciseTime = ZonedDateTime.of(2024, 6, 15, 12, 30, 45, 123_000_000, ZoneOffset.UTC)

        When("Date로 변환 후 복원하면") {
            val date = writeConverter.convert(preciseTime)
            val restored = readConverter.convert(date)

            Then("[U-01] 밀리초 단위까지 instant가 보존된다") {
                restored.toInstant().toEpochMilli() shouldBe preciseTime.toInstant().toEpochMilli()
            }
        }
    }
})
