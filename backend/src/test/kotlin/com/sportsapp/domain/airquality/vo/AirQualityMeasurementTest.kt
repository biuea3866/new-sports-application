package com.sportsapp.domain.airquality.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AirQualityMeasurementTest : BehaviorSpec({

    Given("empty를 호출하면") {
        val measurement = AirQualityMeasurement.empty()

        Then("모든 필드가 null인 측정값을 반환한다") {
            measurement.pm10 shouldBe null
            measurement.pm25 shouldBe null
            measurement.stationName shouldBe null
            measurement.measuredAt shouldBe null
        }
    }
})
