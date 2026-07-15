package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.ProgramResponse
import com.sportsapp.application.facility.usecase.ListProgramsUseCase
import com.sportsapp.application.facility.usecase.RegisterProgramUseCase
import com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class ProgramApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        registerProgramUseCase: RegisterProgramUseCase = mockk(),
        listProgramsUseCase: ListProgramsUseCase = mockk(),
        userId: Long = 1L,
    ) = MockMvcBuilders.standaloneSetup(
        ProgramApiController(registerProgramUseCase, listProgramsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    fun programResponse(id: Long = 1L) = ProgramResponse(
        id = id,
        facilityId = "FAC-01",
        ownerUserId = 1L,
        name = "1:1 PT",
        description = "개인 트레이닝",
        price = BigDecimal("50000"),
        capacity = 1,
        durationMinutes = 60,
    )

    Given("소유자가 PT 상품 등록 요청") {
        val registerProgramUseCase = mockk<RegisterProgramUseCase>()
        every { registerProgramUseCase.execute(any()) } returns programResponse()
        val mockMvc = buildMockMvc(registerProgramUseCase = registerProgramUseCase)

        When("POST /facilities/FAC-01/programs 요청 시") {
            val body = """
                {"name":"1:1 PT","description":"개인 트레이닝","price":50000,"capacity":1,"durationMinutes":60}
            """.trimIndent()
            val result = mockMvc.perform(
                post("/facilities/FAC-01/programs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )

            Then("201과 함께 ProgramResponse 가 반환된다") {
                result.andExpect(status().isCreated)
                    .andExpect(jsonPath("$.facilityId").value("FAC-01"))
                    .andExpect(jsonPath("$.capacity").value(1))
                verify { registerProgramUseCase.execute(match { it.ownerUserId == 1L && it.facilityId == "FAC-01" }) }
            }
        }
    }

    Given("소유자가 아닌 사용자의 등록 요청") {
        val registerProgramUseCase = mockk<RegisterProgramUseCase>()
        every { registerProgramUseCase.execute(any()) } throws UnauthorizedFacilityAccessException("FAC-01")
        val mockMvc = buildMockMvc(registerProgramUseCase = registerProgramUseCase, userId = 99L)

        When("POST /facilities/FAC-01/programs 요청 시") {
            val body = """{"name":"1:1 PT","description":null,"price":0,"capacity":1,"durationMinutes":60}"""
            val result = mockMvc.perform(
                post("/facilities/FAC-01/programs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )

            Then("403이 반환된다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("UNAUTHORIZED_FACILITY_ACCESS"))
            }
        }
    }

    Given("시설상품 목록 조회") {
        val listProgramsUseCase = mockk<ListProgramsUseCase>()
        every { listProgramsUseCase.execute("FAC-01") } returns listOf(programResponse())
        val mockMvc = buildMockMvc(listProgramsUseCase = listProgramsUseCase)

        When("GET /facilities/FAC-01/programs 요청 시") {
            val result = mockMvc.perform(get("/facilities/FAC-01/programs"))

            Then("200과 함께 목록이 반환된다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }
    }
})
