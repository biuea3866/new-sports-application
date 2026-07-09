package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.dto.ProductWithStock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import java.math.BigDecimal
import com.sportsapp.application.goods.dto.ProductCriteria
import com.sportsapp.domain.goods.exception.InvalidPriceRangeException

class SearchProductsUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = SearchProductsUseCase(goodsDomainService)

    Given("priceMin이 priceMax보다 큰 criteria") {
        val criteria = ProductCriteria(
            category = null,
            keyword = null,
            priceMin = BigDecimal("10000"),
            priceMax = BigDecimal("5000"),
            sort = "recent",
            page = 0,
            size = 20,
        )

        When("execute를 호출하면") {
            Then("[U-01] InvalidPriceRangeException이 발생한다") {
                shouldThrow<InvalidPriceRangeException> {
                    useCase.execute(criteria)
                }
            }
        }
    }

    Given("유효한 criteria (키워드 + 가격범위)") {
        val criteria = ProductCriteria(
            category = null,
            keyword = "러닝",
            priceMin = BigDecimal("10000"),
            priceMax = BigDecimal("50000"),
            sort = "recent",
            page = 0,
            size = 20,
        )
        val pageable = criteria.toPageable()
        val emptyPage = PageImpl<ProductWithStock>(emptyList(), pageable, 0)

        every {
            goodsDomainService.search(
                category = null,
                keyword = "러닝",
                priceMin = BigDecimal("10000"),
                priceMax = BigDecimal("50000"),
                sellerType = null,
                pageable = pageable,
            )
        } returns emptyPage

        When("[U-02] execute를 호출하면") {
            val result = useCase.execute(criteria)

            Then("GoodsDomainService.search가 1회 호출되고 결과가 매핑된다") {
                verify(exactly = 1) {
                    goodsDomainService.search(
                        category = null,
                        keyword = "러닝",
                        priceMin = BigDecimal("10000"),
                        priceMax = BigDecimal("50000"),
                        sellerType = null,
                        pageable = pageable,
                    )
                }
                result.totalElements shouldBe 0
            }
        }
    }
})
