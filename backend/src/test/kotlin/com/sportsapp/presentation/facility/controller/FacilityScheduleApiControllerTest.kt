package com.sportsapp.presentation.facility.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sportsapp.application.facility.usecase.AddHolidayUseCase
import com.sportsapp.application.facility.usecase.RegisterOperatingHoursUseCase
import com.sportsapp.application.facility.usecase.RemoveHolidayUseCase
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.facility.vo.OperatingHours
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.geo.Point
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.DayOfWeek
import java.time.LocalTime

private val objectMapper = ObjectMapper().registerModule(JavaTimeModule())

private fun buildMockMvc(
    registerOperatingHoursUseCase: RegisterOperatingHoursUseCase = mockk(),
    addHolidayUseCase: AddHolidayUseCase = mockk(),
    removeHolidayUseCase: RemoveHolidayUseCase = mockk(),
    principal: UserPrincipal = UserPrincipal(id = 1L, email = "owner@test.com", roles = listOf("FACILITY_OWNER")),
) = MockMvcBuilders.standaloneSetup(
    FacilityScheduleApiController(registerOperatingHoursUseCase, addHolidayUseCase, removeHolidayUseCase),
).setCustomArgumentResolvers(AuthenticationPrincipalArgumentResolver())
    .setControllerAdvice(GlobalExceptionHandler())
    .build()
    .also {
        val authentication = mockk<Authentication>()
        every { authentication.principal } returns principal
        SecurityContextHolder.setContext(SecurityContextImpl(authentication))
    }

private fun buildFacility(operatingHoursList: List<OperatingHours> = emptyList()): Facility = Facility(
    id = "f-001", code = "C-001", name = "강남 수영장",
    gu = "강남구", type = "수영장", address = "서울시 강남구",
    location = Point(127.0, 37.5),
    parking = true, tel = "02-0000-0000", homePage = "", eduYn = false,
    meta = emptyMap(), ownerUserId = 1L,
    sidoCode = null, sidoName = null, sigunguCode = null, sigunguName = null,
    operatingHours = operatingHoursList,
)

class FacilityScheduleApiControllerTest : BehaviorSpec({

    Given("소유 시설에 운영시간 등록 요청이 주어졌을 때") {
        val registerOperatingHoursUseCase = mockk<RegisterOperatingHoursUseCase>()
        val hours = listOf(
            OperatingHours(
                dayOfWeek = DayOfWeek.MONDAY,
                openTime = LocalTime.of(6, 0),
                closeTime = LocalTime.of(22, 0),
                capacity = 10,
            ),
        )
        every { registerOperatingHoursUseCase.execute(any()) } returns buildFacility(hours)
        val mockMvc = buildMockMvc(registerOperatingHoursUseCase = registerOperatingHoursUseCase)
        val body = """
            {"operatingHours":[{"dayOfWeek":"MONDAY","openTime":"06:00","closeTime":"22:00","capacity":10}]}
        """.trimIndent()

        When("PUT /facilities/{facilityId}/operating-hours 요청 시") {
            val result = mockMvc.perform(
                put("/facilities/f-001/operating-hours")
                    .contentType("application/json")
                    .content(body),
            )

            Then("200과 함께 등록된 operatingHours를 포함한 FacilityResponse를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.operatingHours[0].dayOfWeek").value("MONDAY"))
                verify(exactly = 1) { registerOperatingHoursUseCase.execute(any()) }
            }
        }
    }

    Given("소유하지 않은 시설에 운영시간 등록을 요청할 때") {
        val registerOperatingHoursUseCase = mockk<RegisterOperatingHoursUseCase>()
        every { registerOperatingHoursUseCase.execute(any()) } throws UnauthorizedFacilityAccessException("f-002")
        val mockMvc = buildMockMvc(registerOperatingHoursUseCase = registerOperatingHoursUseCase)
        val body = """{"operatingHours":[]}"""

        When("PUT /facilities/{facilityId}/operating-hours 요청 시") {
            val result = mockMvc.perform(
                put("/facilities/f-002/operating-hours")
                    .contentType("application/json")
                    .content(body),
            )

            Then("403 Forbidden을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED_FACILITY_ACCESS"))
            }
        }
    }

    Given("소유 시설에 휴무일 추가 요청이 주어졌을 때") {
        val addHolidayUseCase = mockk<AddHolidayUseCase>()
        val facility = buildFacility().also { it.addHoliday(java.time.LocalDate.of(2026, 7, 6)) }
        every { addHolidayUseCase.execute(any()) } returns facility
        val mockMvc = buildMockMvc(addHolidayUseCase = addHolidayUseCase)
        val body = """{"date":"2026-07-06"}"""

        When("POST /facilities/{facilityId}/holidays 요청 시") {
            val result = mockMvc.perform(
                post("/facilities/f-001/holidays")
                    .contentType("application/json")
                    .content(body),
            )

            Then("200과 함께 등록된 holidays를 포함한 FacilityResponse를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.holidays[0]").value("2026-07-06"))
                verify(exactly = 1) { addHolidayUseCase.execute(any()) }
            }
        }
    }

    Given("소유 시설의 휴무일 제거 요청이 주어졌을 때") {
        val removeHolidayUseCase = mockk<RemoveHolidayUseCase>()
        every { removeHolidayUseCase.execute(any()) } returns buildFacility()
        val mockMvc = buildMockMvc(removeHolidayUseCase = removeHolidayUseCase)

        When("DELETE /facilities/{facilityId}/holidays?date=2026-07-06 요청 시") {
            val result = mockMvc.perform(delete("/facilities/f-001/holidays?date=2026-07-06"))

            Then("200과 함께 FacilityResponse를 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { removeHolidayUseCase.execute(any()) }
            }
        }
    }
})
