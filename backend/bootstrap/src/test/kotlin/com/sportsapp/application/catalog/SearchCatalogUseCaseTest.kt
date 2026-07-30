package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SearchCatalogUseCaseTest : BehaviorSpec({

    val catalogCompositionService = mockk<CatalogCompositionService>()
    val useCase = SearchCatalogUseCase(catalogCompositionService)

    Given("유효한 catalog 검색 조건이 주어졌을 때") {
        val criteria = CatalogSearchCriteria(keyword = "요가", itemType = null, sellerType = null, page = 0, size = 20)
        val expectedResponse = CatalogSearchResponse(items = emptyList(), page = 0, size = 20, failedDomains = emptyList())
        every { catalogCompositionService.search(criteria) } returns expectedResponse

        When("execute를 호출하면") {
            val result = useCase.execute(criteria)

            Then("CatalogCompositionService.search에 criteria를 그대로 위임하고 결과를 반환한다") {
                verify(exactly = 1) { catalogCompositionService.search(criteria) }
                result shouldBe expectedResponse
            }
        }
    }
})
