package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.usecase.BackfillFacilityRegionUseCase
import com.sportsapp.application.facility.usecase.ImportLegacyFacilitiesUseCase
import com.sportsapp.application.facility.usecase.ImportPublicFacilitiesUseCase
import com.sportsapp.domain.facility.dto.BackfillResult
import com.sportsapp.domain.facility.exception.FacilityRegionBackfillInProgressException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private fun buildMockMvc(backfillFacilityRegionUseCase: BackfillFacilityRegionUseCase) = MockMvcBuilders.standaloneSetup(
    AdminFacilityApiController(
        mockk<ImportLegacyFacilitiesUseCase>(),
        mockk<ImportPublicFacilitiesUseCase>(),
        backfillFacilityRegionUseCase,
    ),
).setControllerAdvice(GlobalExceptionHandler()).build()

class AdminFacilityApiControllerTest : BehaviorSpec({

    Given("정상적으로 백필이 수행 가능한 상태") {
        val backfillFacilityRegionUseCase = mockk<BackfillFacilityRegionUseCase>()
        every { backfillFacilityRegionUseCase.execute(100) } returns BackfillResult(updated = 8, unspecified = 1)
        val mockMvc = buildMockMvc(backfillFacilityRegionUseCase)

        When("POST /admin/facilities/backfill-region 요청 시") {
            val result = mockMvc.perform(post("/admin/facilities/backfill-region"))

            Then("200과 함께 updated·unspecified 카운트를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.updated").value(8))
                    .andExpect(jsonPath("$.unspecified").value(1))
                verify(exactly = 1) { backfillFacilityRegionUseCase.execute(100) }
            }
        }
    }

    Given("pageSize 쿼리 파라미터를 지정한 요청") {
        val backfillFacilityRegionUseCase = mockk<BackfillFacilityRegionUseCase>()
        every { backfillFacilityRegionUseCase.execute(50) } returns BackfillResult(updated = 5, unspecified = 0)
        val mockMvc = buildMockMvc(backfillFacilityRegionUseCase)

        When("POST /admin/facilities/backfill-region?pageSize=50 요청 시") {
            val result = mockMvc.perform(post("/admin/facilities/backfill-region?pageSize=50"))

            Then("지정한 pageSize 그대로 UseCase를 호출한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { backfillFacilityRegionUseCase.execute(50) }
            }
        }
    }

    Given("이미 다른 백필이 진행 중인 상태") {
        val backfillFacilityRegionUseCase = mockk<BackfillFacilityRegionUseCase>()
        every { backfillFacilityRegionUseCase.execute(any()) } throws FacilityRegionBackfillInProgressException()
        val mockMvc = buildMockMvc(backfillFacilityRegionUseCase)

        When("POST /admin/facilities/backfill-region 요청 시") {
            val result = mockMvc.perform(post("/admin/facilities/backfill-region"))

            Then("409 Conflict를 반환한다") {
                result.andExpect(status().isConflict)
                    .andExpect(jsonPath("$.code").value("FACILITY_REGION_BACKFILL_IN_PROGRESS"))
            }
        }
    }
})
