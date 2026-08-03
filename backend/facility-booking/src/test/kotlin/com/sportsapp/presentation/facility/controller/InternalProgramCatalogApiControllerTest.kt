package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.InternalProgramCatalogItemResponse
import com.sportsapp.application.facility.usecase.SearchProgramCatalogUseCase
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * edge catalog 통합검색(BE-07)의 CatalogSearchGateway.searchPrograms 원격 구현(2단계)이 호출할
 * 공급자 엔드포인트 계약 검증 (S2-04).
 */
class InternalProgramCatalogApiControllerTest : BehaviorSpec({

    fun buildMockMvc(searchProgramCatalogUseCase: SearchProgramCatalogUseCase) = MockMvcBuilders.standaloneSetup(
        InternalProgramCatalogApiController(searchProgramCatalogUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    fun catalogItem(sourceId: Long = 1L, title: String = "1:1 PT") = InternalProgramCatalogItemResponse(
        sourceId = sourceId,
        title = title,
        price = BigDecimal("50000"),
        createdAt = ZonedDateTime.now(),
    )

    Given("키워드·페이지 조건으로 시설상품 목록을 요청하면") {
        val searchProgramCatalogUseCase = mockk<SearchProgramCatalogUseCase>()
        every { searchProgramCatalogUseCase.execute(any()) } returns listOf(catalogItem())
        val mockMvc = buildMockMvc(searchProgramCatalogUseCase)

        When("GET /internal/catalog/programs?keyword=PT&page=0&size=20 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/programs?keyword=PT&page=0&size=20"))

            Then("200과 함께 계약 필드로 정규화된 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(1))
                    .andExpect(jsonPath("$[0].title").value("1:1 PT"))
                verify {
                    searchProgramCatalogUseCase.execute(
                        match { it.keyword == "PT" && it.page == 0 && it.size == 20 },
                    )
                }
            }

            Then("Program 엔티티 필드(facilityId·ownerUserId·capacity·durationMinutes)는 노출되지 않는다") {
                result.andExpect(jsonPath("$[0].facilityId").doesNotExist())
                    .andExpect(jsonPath("$[0].ownerUserId").doesNotExist())
                    .andExpect(jsonPath("$[0].capacity").doesNotExist())
                    .andExpect(jsonPath("$[0].durationMinutes").doesNotExist())
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val searchProgramCatalogUseCase = mockk<SearchProgramCatalogUseCase>()
        every { searchProgramCatalogUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(searchProgramCatalogUseCase)

        When("GET /internal/catalog/programs 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/programs"))

            Then("200과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
