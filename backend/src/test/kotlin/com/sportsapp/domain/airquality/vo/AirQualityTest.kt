package com.sportsapp.domain.airquality.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime

class AirQualityTest : BehaviorSpec({

    Given("pm10은 BAD 등급, pm25는 MODERATE 등급인 측정값") {
        val measurement = AirQualityMeasurement(
            pm10 = 90,
            pm25 = 20,
            stationName = "종로구",
            measuredAt = ZonedDateTime.now(),
        )

        When("of로 조립하면") {
            val airQuality = AirQuality.of(measurement)

            Then("대표 등급은 더 나쁜 BAD가 된다") {
                airQuality.pm10Grade shouldBe AirQualityGrade.BAD
                airQuality.pm25Grade shouldBe AirQualityGrade.MODERATE
                airQuality.representativeGrade shouldBe AirQualityGrade.BAD
            }
        }
    }

    Given("pm10만 존재하고 pm25가 null인 측정값") {
        val measurement = AirQualityMeasurement(
            pm10 = 40,
            pm25 = null,
            stationName = "종로구",
            measuredAt = ZonedDateTime.now(),
        )

        When("of로 조립하면") {
            val airQuality = AirQuality.of(measurement)

            Then("대표 등급은 UNKNOWN을 제외하고 pm10 등급을 따른다") {
                airQuality.pm10Grade shouldBe AirQualityGrade.MODERATE
                airQuality.pm25Grade shouldBe AirQualityGrade.UNKNOWN
                airQuality.representativeGrade shouldBe AirQualityGrade.MODERATE
            }
        }
    }

    Given("pm10과 pm25 모두 null인 측정값") {
        val measurement = AirQualityMeasurement.empty()

        When("of로 조립하면") {
            val airQuality = AirQuality.of(measurement)

            Then("대표 등급은 UNKNOWN이 된다") {
                airQuality.representativeGrade shouldBe AirQualityGrade.UNKNOWN
            }
        }
    }

    Given("empty를 호출하면") {
        val airQuality = AirQuality.empty()

        Then("모든 값이 null·UNKNOWN인 AirQuality를 반환한다") {
            airQuality.pm10 shouldBe null
            airQuality.pm25 shouldBe null
            airQuality.pm10Grade shouldBe AirQualityGrade.UNKNOWN
            airQuality.pm25Grade shouldBe AirQualityGrade.UNKNOWN
            airQuality.representativeGrade shouldBe AirQualityGrade.UNKNOWN
            airQuality.stationName shouldBe null
            airQuality.measuredAt shouldBe null
        }
    }
})
