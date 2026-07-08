package com.sportsapp.domain.airquality.service

import com.sportsapp.domain.airquality.gateway.AirQualityGateway
import com.sportsapp.domain.airquality.vo.AirQualityGrade
import com.sportsapp.domain.airquality.vo.AirQualityMeasurement
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class AirQualityDomainServiceTest : BehaviorSpec({

    Given("gateway가 정상 측정값을 반환할 때") {
        val airQualityGateway = mockk<AirQualityGateway>()
        every { airQualityGateway.current(37.5, 127.0) } returns AirQualityMeasurement(
            pm10 = 90,
            pm25 = 20,
            stationName = "종로구",
            measuredAt = ZonedDateTime.now(),
        )
        val airQualityDomainService = AirQualityDomainService(airQualityGateway)

        When("current를 호출하면") {
            val airQuality = airQualityDomainService.current(37.5, 127.0)

            Then("측정값을 등급이 조립된 AirQuality로 변환해 반환한다") {
                airQuality.pm10 shouldBe 90
                airQuality.pm25 shouldBe 20
                airQuality.representativeGrade shouldBe AirQualityGrade.BAD
            }
        }
    }

    Given("gateway가 empty를 반환할 때") {
        val airQualityGateway = mockk<AirQualityGateway>()
        every { airQualityGateway.current(37.5, 127.0) } returns AirQualityMeasurement.empty()
        val airQualityDomainService = AirQualityDomainService(airQualityGateway)

        When("current를 호출하면") {
            val airQuality = airQualityDomainService.current(37.5, 127.0)

            Then("representativeGrade가 UNKNOWN인 AirQuality를 반환한다") {
                airQuality.representativeGrade shouldBe AirQualityGrade.UNKNOWN
            }
        }
    }
})
