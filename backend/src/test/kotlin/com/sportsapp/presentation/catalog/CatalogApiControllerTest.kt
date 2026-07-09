package com.sportsapp.presentation.catalog

import com.sportsapp.application.catalog.SearchCatalogUseCase
import com.sportsapp.application.catalog.dto.CatalogItem
import com.sportsapp.application.catalog.dto.CatalogItemType
import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import com.sportsapp.domain.goods.vo.SellerType
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

class CatalogApiControllerTest : BehaviorSpec({

    fun buildMockMvc(searchCatalogUseCase: SearchCatalogUseCase) =
        MockMvcBuilders.standaloneSetup(CatalogApiController(searchCatalogUseCase)).build()

    Given("인증 헤더 없이 catalog 검색을 요청하는 상황") {
        val searchCatalogUseCase = mockk<SearchCatalogUseCase>()
        val item = CatalogItem(
            itemType = CatalogItemType.PRODUCT,
            sourceId = 1L,
            title = "러닝화",
            price = BigDecimal("50000"),
            sellerType = SellerType.B2C,
            status = "ACTIVE",
            detailPath = "/products/1",
            createdAt = ZonedDateTime.now(),
        )
        every { searchCatalogUseCase.execute(any()) } returns CatalogSearchResponse(
            items = listOf(item),
            page = 0,
            size = 20,
            failedDomains = emptyList(),
        )
        val mockMvc = buildMockMvc(searchCatalogUseCase)

        When("GET /api/catalog를 호출하면") {
            val result = mockMvc.perform(get("/api/catalog"))

            Then("미인증도 200과 함께 조합된 결과가 반환된다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.items.length()").value(1))
                    .andExpect(jsonPath("$.items[0].itemType").value("PRODUCT"))
                    .andExpect(jsonPath("$.items[0].sellerType").value("B2C"))
                    .andExpect(jsonPath("$.failedDomains.length()").value(0))
            }
        }
    }

    Given("keyword·itemType·sellerType·page·size 쿼리 파라미터가 주어진 상황") {
        val searchCatalogUseCase = mockk<SearchCatalogUseCase>()
        every { searchCatalogUseCase.execute(any()) } returns CatalogSearchResponse(
            items = emptyList(),
            page = 1,
            size = 10,
            failedDomains = emptyList(),
        )
        val mockMvc = buildMockMvc(searchCatalogUseCase)

        When("GET /api/catalog?keyword=요가&itemType=RECRUITMENT&sellerType=B2B&page=1&size=10 을 호출하면") {
            mockMvc.perform(
                get("/api/catalog")
                    .param("keyword", "요가")
                    .param("itemType", "RECRUITMENT")
                    .param("sellerType", "B2B")
                    .param("page", "1")
                    .param("size", "10"),
            )

            Then("쿼리 파라미터가 CatalogSearchCriteria로 정확히 변환되어 위임된다") {
                verify(exactly = 1) {
                    searchCatalogUseCase.execute(
                        CatalogSearchCriteria(
                            keyword = "요가",
                            itemType = CatalogItemType.RECRUITMENT,
                            sellerType = SellerType.B2B,
                            page = 1,
                            size = 10,
                        ),
                    )
                }
            }
        }
    }

    Given("결과가 없는 catalog 검색 상황") {
        val searchCatalogUseCase = mockk<SearchCatalogUseCase>()
        every { searchCatalogUseCase.execute(any()) } returns CatalogSearchResponse(
            items = emptyList(),
            page = 0,
            size = 20,
            failedDomains = emptyList(),
        )
        val mockMvc = buildMockMvc(searchCatalogUseCase)

        When("GET /api/catalog?keyword=클라이밍 을 호출하면") {
            val result = mockMvc.perform(get("/api/catalog").param("keyword", "클라이밍"))

            Then("200과 함께 빈 items가 반환된다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.items.length()").value(0))
            }
        }
    }
})
