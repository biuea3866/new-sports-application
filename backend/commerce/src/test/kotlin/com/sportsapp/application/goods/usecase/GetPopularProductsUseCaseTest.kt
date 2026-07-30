package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class GetPopularProductsUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetPopularProductsUseCase(goodsDomainService)

    fun makeSnapshot(id: Long, name: String, category: ProductCategory) = PopularProductSnapshot(
        id = id,
        name = name,
        category = category,
        price = BigDecimal("50000"),
        description = "desc",
        imageUrl = "https://example.com/img.jpg",
        status = ProductStatus.ACTIVE,
    )

    Given("[U-01] FOOTWEAR 카테고리 인기 상품 조회") {
        val category = ProductCategory.FOOTWEAR
        val snapshots = listOf(makeSnapshot(1L, "나이키", category), makeSnapshot(2L, "아디다스", category))
        every { goodsDomainService.getPopular(category) } returns snapshots

        When("execute를 호출하면") {
            val result = useCase.execute(category)

            Then("GoodsDomainService.getPopular가 1회 호출되고 PopularProductResponse로 매핑된다 (id 포함)") {
                verify(exactly = 1) { goodsDomainService.getPopular(category) }
                result.size shouldBe 2
                result[0].id shouldBe 1L
                result[0].name shouldBe "나이키"
                result[1].id shouldBe 2L
                result[1].name shouldBe "아디다스"
            }
        }
    }

    Given("[U-01] 인기 상품 없는 카테고리") {
        val category = ProductCategory.ACCESSORY
        every { goodsDomainService.getPopular(category) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute(category)

            Then("빈 리스트가 반환된다") {
                result.size shouldBe 0
            }
        }
    }

    Given("[U-02] 캐시 invalidate 시나리오") {
        val category = ProductCategory.FOOTWEAR
        every { goodsDomainService.invalidatePopularCache(category) } returns Unit

        When("InvalidateCacheUseCase.execute를 호출하면") {
            val invalidateUseCase = InvalidateCacheUseCase(goodsDomainService)
            invalidateUseCase.execute(category)

            Then("GoodsDomainService.invalidatePopularCache가 1회 호출된다") {
                verify(exactly = 1) { goodsDomainService.invalidatePopularCache(category) }
                verify(exactly = 0) { goodsDomainService.invalidatePopularCache(ProductCategory.APPAREL) }
            }
        }
    }
})
