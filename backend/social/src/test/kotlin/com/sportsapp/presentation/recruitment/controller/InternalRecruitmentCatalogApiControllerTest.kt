package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.InternalRecruitmentCatalogItemResponse
import com.sportsapp.application.recruitment.usecase.SearchRecruitmentsForCatalogUseCase
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * catalog 통합검색(BE-07) 원격 공급 엔드포인트 (S2-05, edge CatalogSearchGateway.searchRecruitments
 * 2단계 구현 대상). 신원 헤더 없이도 응답해야 하는 공개 조회 비대칭을 검증한다.
 */
class InternalRecruitmentCatalogApiControllerTest : BehaviorSpec({

    fun buildMockMvc(searchRecruitmentsForCatalogUseCase: SearchRecruitmentsForCatalogUseCase = mockk()) =
        MockMvcBuilders.standaloneSetup(
            InternalRecruitmentCatalogApiController(searchRecruitmentsForCatalogUseCase),
        ).setControllerAdvice(GlobalExceptionHandler()).build()

    fun catalogItem(sourceId: Long): InternalRecruitmentCatalogItemResponse = InternalRecruitmentCatalogItemResponse(
        sourceId = sourceId,
        title = "주말 축구 모임",
        price = BigDecimal("10000"),
        status = RecruitmentStatus.OPEN,
        createdAt = ZonedDateTime.now(),
    )

    Given("신원 헤더 없이 오픈 모집 목록을 조회하면") {
        val searchRecruitmentsForCatalogUseCase = mockk<SearchRecruitmentsForCatalogUseCase>()
        every { searchRecruitmentsForCatalogUseCase.execute(null, 0, 20) } returns listOf(catalogItem(1L))
        val mockMvc = buildMockMvc(searchRecruitmentsForCatalogUseCase)

        When("GET /internal/catalog/recruitments 요청 시 (신원 헤더 없음)") {
            val result = mockMvc.perform(get("/internal/catalog/recruitments"))

            Then("200과 함께 계약 필드만 담은 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(1))
                    .andExpect(jsonPath("$[0].title").value("주말 축구 모임"))
                    .andExpect(jsonPath("$[0].price").value(10000))
                    .andExpect(jsonPath("$[0].status").value("OPEN"))
                    .andExpect(jsonPath("$[0].description").doesNotExist())
                    .andExpect(jsonPath("$[0].capacity").doesNotExist())
            }
        }
    }

    Given("keyword로 검색하면") {
        val searchRecruitmentsForCatalogUseCase = mockk<SearchRecruitmentsForCatalogUseCase>()
        every { searchRecruitmentsForCatalogUseCase.execute("축구", 0, 20) } returns listOf(catalogItem(2L))
        val mockMvc = buildMockMvc(searchRecruitmentsForCatalogUseCase)

        When("GET /internal/catalog/recruitments?keyword=축구 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/recruitments").param("keyword", "축구"))

            Then("200과 함께 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].sourceId").value(2))
            }
        }
    }

    Given("검색 결과가 0건일 때") {
        val searchRecruitmentsForCatalogUseCase = mockk<SearchRecruitmentsForCatalogUseCase>()
        every { searchRecruitmentsForCatalogUseCase.execute(null, 0, 20) } returns emptyList()
        val mockMvc = buildMockMvc(searchRecruitmentsForCatalogUseCase)

        When("GET /internal/catalog/recruitments 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/recruitments"))

            Then("200과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }

    Given("page·size 쿼리 파라미터로 조회하면") {
        val searchRecruitmentsForCatalogUseCase = mockk<SearchRecruitmentsForCatalogUseCase>()
        every { searchRecruitmentsForCatalogUseCase.execute(null, 2, 5) } returns listOf(catalogItem(3L))
        val mockMvc = buildMockMvc(searchRecruitmentsForCatalogUseCase)

        When("GET /internal/catalog/recruitments?page=2&size=5 요청 시") {
            val result = mockMvc.perform(
                get("/internal/catalog/recruitments").param("page", "2").param("size", "5"),
            )

            Then("전달받은 page·size를 그대로 UseCase에 위임한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].sourceId").value(3))
            }
        }
    }
})
