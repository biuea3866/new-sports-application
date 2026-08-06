package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.InternalGoodsCatalogCriteria
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private val CREATED_AT: ZonedDateTime = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchGoods` 원격 구현(2단계) 공급자 (S2-03).
 *
 * 핵심 규약은 **한정판 판정을 공급자가 하지 않는다**는 것이다 — `itemType`(PRODUCT/LIMITED_DROP)·
 * `sourceId`(product.id vs limitedDropId)·`detailPath`·`status`(product.status vs limitedDropStatus)는
 * 전부 파사드가 파생하므로, 공급자는 그 판정에 필요한 **원자값**을 그대로 실어 보낸다. 매핑 위치를
 * 옮기면 섀도 응답 동일성 비교가 성립하지 않는다.
 */
class SearchGoodsCatalogUseCaseTest : BehaviorSpec({

    fun product(id: Long, name: String, sellerType: SellerType?): Product = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.name } returns name
        every { price } returns BigDecimal("50000")
        every { this@mockk.sellerType } returns sellerType
        every { status } returns ProductStatus.ACTIVE
        every { createdAt } returns CREATED_AT
    }

    Given("일반 상품과 한정판 상품이 섞인 검색 결과") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        val page: Page<ProductWithStock> = PageImpl(
            listOf(
                ProductWithStock(product = product(1L, "러닝화", SellerType.B2C), stockQuantity = 3),
                ProductWithStock(
                    product = product(9L, "한정판 저지", SellerType.B2B),
                    stockQuantity = 1,
                    limitedDropId = 99L,
                    limitedDropStatus = LimitedDropStatus.SOLD_OUT,
                ),
            ),
            pageable,
            2,
        )
        every {
            goodsDomainService.search(
                category = null,
                keyword = "신발",
                priceMin = null,
                priceMax = null,
                sellerType = null,
                pageable = pageable,
            )
        } returns page
        val useCase = SearchGoodsCatalogUseCase(goodsDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(
                InternalGoodsCatalogCriteria(keyword = "신발", sellerType = null, page = 0, size = 20),
            )

            Then("일반 상품은 한정판 원자값이 비어 있다 — 파사드가 PRODUCT 로 판정한다") {
                result[0].productId shouldBe 1L
                result[0].limitedDropId shouldBe null
                result[0].limitedDropStatus shouldBe null
                result[0].productStatus shouldBe ProductStatus.ACTIVE
            }

            Then("한정판 상품은 limitedDropId·limitedDropStatus 를 그대로 실어 보낸다 — 파사드의 판정 입력") {
                result[1].productId shouldBe 9L
                result[1].limitedDropId shouldBe 99L
                result[1].limitedDropStatus shouldBe LimitedDropStatus.SOLD_OUT
            }

            Then("공급자는 itemType·sourceId·detailPath·status 를 만들지 않는다 (파사드 책임)") {
                val fieldNames = result[0]::class.members.map { it.name }
                fieldNames.contains("itemType") shouldBe false
                fieldNames.contains("sourceId") shouldBe false
                fieldNames.contains("detailPath") shouldBe false
                fieldNames.contains("status") shouldBe false
            }

            Then("판매자 유형·제목·가격·생성 시각은 상품 자기 데이터로 채운다") {
                result[0].title shouldBe "러닝화"
                result[0].price shouldBe BigDecimal("50000")
                result[0].sellerType shouldBe SellerType.B2C
                result[0].createdAt shouldBe CREATED_AT
                result[1].sellerType shouldBe SellerType.B2B
            }
        }
    }

    Given("판매자 유형 필터가 지정된 검색") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        every {
            goodsDomainService.search(
                category = null,
                keyword = null,
                priceMin = null,
                priceMax = null,
                sellerType = SellerType.B2B,
                pageable = pageable,
            )
        } returns PageImpl(emptyList(), pageable, 0)
        val useCase = SearchGoodsCatalogUseCase(goodsDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(
                InternalGoodsCatalogCriteria(keyword = null, sellerType = SellerType.B2B, page = 0, size = 20),
            )

            Then("도메인에 그 필터를 그대로 전달하고 카테고리·가격 조건은 쓰지 않는다") {
                verify(exactly = 1) {
                    goodsDomainService.search(
                        category = null,
                        keyword = null,
                        priceMin = null,
                        priceMax = null,
                        sellerType = SellerType.B2B,
                        pageable = pageable,
                    )
                }
            }

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("이 UseCase 의 의존 구성을") {
        Then("Repository 를 직접 주입받지 않는다 (DomainService 경유)") {
            val parameterTypes = SearchGoodsCatalogUseCase::class.java.declaredConstructors.single().parameterTypes
            parameterTypes.size shouldBe 1
            parameterTypes[0] shouldBe GoodsDomainService::class.java
        }

        Then("검색 조건에 ProductCategory 를 노출하지 않는다 — 파사드가 쓰지 않는 조건이다") {
            InternalGoodsCatalogCriteria::class.members.map { it.name }.contains("category") shouldBe false
            ProductCategory.entries.isNotEmpty() shouldBe true
        }
    }
})
