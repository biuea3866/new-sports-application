package com.sportsapp.presentation.airquality.controller

import com.sportsapp.application.airquality.usecase.GetAirQualityUseCase
import com.sportsapp.domain.airquality.vo.AirQuality
import com.sportsapp.domain.airquality.vo.AirQualityGrade
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private fun buildMockMvc(getAirQualityUseCase: GetAirQualityUseCase) = MockMvcBuilders.standaloneSetup(
    AirQualityApiController(getAirQualityUseCase),
).setControllerAdvice(GlobalExceptionHandler()).build()

class AirQualityApiControllerTest : BehaviorSpec({

    Given("좌표 기준 정상 측정값이 존재할 때") {
        val getAirQualityUseCase = mockk<GetAirQualityUseCase>()
        val measuredAt = ZonedDateTime.now()
        every { getAirQualityUseCase.execute(37.5, 127.0) } returns AirQuality(
            pm10 = 90,
            pm25 = 20,
            pm10Grade = AirQualityGrade.BAD,
            pm25Grade = AirQualityGrade.MODERATE,
            representativeGrade = AirQualityGrade.BAD,
            stationName = "종로구",
            measuredAt = measuredAt,
        )
        val mockMvc = buildMockMvc(getAirQualityUseCase)

        When("GET /air-quality?lat=37.5&lng=127.0 요청 시") {
            val result = mockMvc.perform(get("/air-quality?lat=37.5&lng=127.0"))

            Then("200과 함께 pm10·pm25·대표 등급을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.pm10").value(90))
                    .andExpect(jsonPath("$.pm25").value(20))
                    .andExpect(jsonPath("$.representativeGrade").value("BAD"))
                    .andExpect(jsonPath("$.stationName").value("종로구"))
                    .andExpect(jsonPath("$.measuredAt").value(measuredAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
                verify(exactly = 1) { getAirQualityUseCase.execute(37.5, 127.0) }
            }
        }
    }

    Given("Gateway가 degrade(empty)된 상태일 때") {
        val getAirQualityUseCase = mockk<GetAirQualityUseCase>()
        every { getAirQualityUseCase.execute(37.5, 127.0) } returns AirQuality.empty()
        val mockMvc = buildMockMvc(getAirQualityUseCase)

        When("GET /air-quality?lat=37.5&lng=127.0 요청 시") {
            val result = mockMvc.perform(get("/air-quality?lat=37.5&lng=127.0"))

            Then("200과 함께 값 null·UNKNOWN 등급을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.pm10").doesNotExist())
                    .andExpect(jsonPath("$.pm25").doesNotExist())
                    .andExpect(jsonPath("$.representativeGrade").value("UNKNOWN"))
                    .andExpect(jsonPath("$.stationName").doesNotExist())
            }
        }
    }

    Given("lat 파라미터가 누락된 요청") {
        val getAirQualityUseCase = mockk<GetAirQualityUseCase>()
        val mockMvc = buildMockMvc(getAirQualityUseCase)

        When("GET /air-quality?lng=127.0 요청 시") {
            val result = mockMvc.perform(get("/air-quality?lng=127.0"))

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { getAirQualityUseCase.execute(any(), any()) }
            }
        }
    }

    Given("lng 파라미터가 누락된 요청") {
        val getAirQualityUseCase = mockk<GetAirQualityUseCase>()
        val mockMvc = buildMockMvc(getAirQualityUseCase)

        When("GET /air-quality?lat=37.5 요청 시") {
            val result = mockMvc.perform(get("/air-quality?lat=37.5"))

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { getAirQualityUseCase.execute(any(), any()) }
            }
        }
    }

    Given("비수치 좌표 값을 담은 요청") {
        val getAirQualityUseCase = mockk<GetAirQualityUseCase>()
        val mockMvc = buildMockMvc(getAirQualityUseCase)

        When("GET /air-quality?lat=abc&lng=127.0 요청 시") {
            val result = mockMvc.perform(get("/air-quality?lat=abc&lng=127.0"))

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { getAirQualityUseCase.execute(any(), any()) }
            }
        }
    }
})
