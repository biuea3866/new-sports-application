package com.sportsapp.application.airquality

import com.sportsapp.application.airquality.usecase.GetAirQualityUseCase
import com.sportsapp.domain.airquality.service.AirQualityDomainService
import com.sportsapp.domain.airquality.vo.AirQuality
import com.sportsapp.domain.airquality.vo.AirQualityGrade
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class GetAirQualityUseCaseTest : BehaviorSpec({

    Given("AirQualityDomainService가 정상 측정값 기반 AirQuality를 반환할 때") {
        val airQualityDomainService = mockk<AirQualityDomainService>()
        val measuredAt = ZonedDateTime.now()
        val airQuality = AirQuality(
            pm10 = 90,
            pm25 = 20,
            pm10Grade = AirQualityGrade.BAD,
            pm25Grade = AirQualityGrade.MODERATE,
            representativeGrade = AirQualityGrade.BAD,
            stationName = "종로구",
            measuredAt = measuredAt,
        )
        every { airQualityDomainService.current(37.5, 127.0) } returns airQuality
        val getAirQualityUseCase = GetAirQualityUseCase(airQualityDomainService)

        When("execute(lat, lng)를 호출하면") {
            val result = getAirQualityUseCase.execute(37.5, 127.0)

            Then("AirQualityDomainService가 반환한 AirQuality를 그대로 반환한다") {
                result shouldBe airQuality
                verify(exactly = 1) { airQualityDomainService.current(37.5, 127.0) }
            }
        }
    }

    Given("AirQualityDomainService가 empty AirQuality를 반환할 때") {
        val airQualityDomainService = mockk<AirQualityDomainService>()
        every { airQualityDomainService.current(37.5, 127.0) } returns AirQuality.empty()
        val getAirQualityUseCase = GetAirQualityUseCase(airQualityDomainService)

        When("execute(lat, lng)를 호출하면") {
            val result = getAirQualityUseCase.execute(37.5, 127.0)

            Then("representativeGrade가 UNKNOWN인 empty AirQuality를 반환한다") {
                result.representativeGrade shouldBe AirQualityGrade.UNKNOWN
                result.pm10 shouldBe null
                result.pm25 shouldBe null
            }
        }
    }
})
