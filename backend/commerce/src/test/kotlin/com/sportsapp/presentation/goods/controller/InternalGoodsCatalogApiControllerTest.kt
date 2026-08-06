package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.InternalGoodsCatalogCriteria
import com.sportsapp.application.goods.dto.InternalGoodsCatalogItemResponse
import com.sportsapp.application.goods.usecase.SearchGoodsCatalogUseCase
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.SellerType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.hamcrest.Matchers.nullValue
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import sportsapp.testkit.presentation.support.productionEquivalentJsonConverter

/**
 * catalog 통합검색(BE-07)의 goods 원격 공급 엔드포인트 계약 검증 (S2-03).
 *
 * 핵심은 **한정판 판정 원자값이 응답 JSON 에 실린다**는 것이다 — `itemType`·`sourceId`·`detailPath`·
 * `status` 는 파사드가 `limitedDropId`·`limitedDropStatus` 로 파생하므로, 그 원자값이 빠지면 파사드가
 * 한정판을 일반 상품으로 오노출한다(품절 한정판이 ACTIVE 로 보이는 사고).
 */
class InternalGoodsCatalogApiControllerTest : BehaviorSpec({

    fun buildMockMvc(searchGoodsCatalogUseCase: SearchGoodsCatalogUseCase) = MockMvcBuilders
        .standaloneSetup(InternalGoodsCatalogApiController(searchGoodsCatalogUseCase))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(productionEquivalentJsonConverter())
        .build()

    fun catalogItem(
        productId: Long = 1L,
        limitedDropId: Long? = null,
        limitedDropStatus: LimitedDropStatus? = null,
        sellerType: SellerType? = SellerType.B2C,
    ) = InternalGoodsCatalogItemResponse(
        productId = productId,
        limitedDropId = limitedDropId,
        limitedDropStatus = limitedDropStatus,
        title = "러닝화",
        price = BigDecimal("50000"),
        sellerType = sellerType,
        productStatus = ProductStatus.ACTIVE,
        createdAt = ZonedDateTime.now(),
    )

    Given("신원 헤더 없이 상품 카탈로그를 조회하면 (공개 조회)") {
        val searchGoodsCatalogUseCase = mockk<SearchGoodsCatalogUseCase>()
        every {
            searchGoodsCatalogUseCase.execute(
                InternalGoodsCatalogCriteria(keyword = null, sellerType = null, page = 0, size = 20),
            )
        } returns listOf(catalogItem())
        val mockMvc = buildMockMvc(searchGoodsCatalogUseCase)

        When("GET /internal/catalog/goods 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/goods"))

            Then("200 과 함께 계약 필드를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].productId").value(1))
                    .andExpect(jsonPath("$[0].title").value("러닝화"))
                    .andExpect(jsonPath("$[0].price").value(50000))
                    .andExpect(jsonPath("$[0].sellerType").value("B2C"))
                    .andExpect(jsonPath("$[0].productStatus").value("ACTIVE"))
                    .andExpect(jsonPath("$[0].createdAt").exists())
            }

            Then("일반 상품은 한정판 원자값이 키를 유지한 채 null 이다") {
                result.andExpect(jsonPath("$[0].limitedDropId").hasJsonPath())
                    .andExpect(jsonPath("$[0].limitedDropId").value(nullValue()))
                    .andExpect(jsonPath("$[0].limitedDropStatus").value(nullValue()))
            }

            Then("파사드가 파생하는 필드(itemType·sourceId·detailPath·status)는 응답에 없다") {
                result.andExpect(jsonPath("$[0].itemType").doesNotExist())
                    .andExpect(jsonPath("$[0].sourceId").doesNotExist())
                    .andExpect(jsonPath("$[0].detailPath").doesNotExist())
                    .andExpect(jsonPath("$[0].status").doesNotExist())
            }

            Then("Product 엔티티 필드(description·imageUrl·ownerId)는 노출되지 않는다") {
                result.andExpect(jsonPath("$[0].description").doesNotExist())
                    .andExpect(jsonPath("$[0].imageUrl").doesNotExist())
                    .andExpect(jsonPath("$[0].ownerId").doesNotExist())
            }
        }
    }

    Given("한정판 상품이 포함된 결과이면") {
        val searchGoodsCatalogUseCase = mockk<SearchGoodsCatalogUseCase>()
        every { searchGoodsCatalogUseCase.execute(any()) } returns listOf(
            catalogItem(
                productId = 9L,
                limitedDropId = 99L,
                limitedDropStatus = LimitedDropStatus.SOLD_OUT,
                sellerType = SellerType.B2B,
            ),
        )
        val mockMvc = buildMockMvc(searchGoodsCatalogUseCase)

        When("GET /internal/catalog/goods 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/goods"))

            Then("한정판 판정 원자값을 그대로 실어 보낸다 — 파사드가 LIMITED_DROP·품절 상태를 파생한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].productId").value(9))
                    .andExpect(jsonPath("$[0].limitedDropId").value(99))
                    .andExpect(jsonPath("$[0].limitedDropStatus").value("SOLD_OUT"))
                    .andExpect(jsonPath("$[0].sellerType").value("B2B"))
            }
        }
    }

    Given("키워드·판매자유형·페이지 조건을 지정하면") {
        val searchGoodsCatalogUseCase = mockk<SearchGoodsCatalogUseCase>()
        every {
            searchGoodsCatalogUseCase.execute(
                InternalGoodsCatalogCriteria(keyword = "저지", sellerType = SellerType.B2B, page = 2, size = 5),
            )
        } returns emptyList()
        val mockMvc = buildMockMvc(searchGoodsCatalogUseCase)

        When("GET /internal/catalog/goods?keyword=저지&sellerType=B2B&page=2&size=5 요청 시") {
            val result = mockMvc.perform(
                get("/internal/catalog/goods")
                    .param("keyword", "저지")
                    .param("sellerType", "B2B")
                    .param("page", "2")
                    .param("size", "5"),
            )

            Then("조건을 값 객체로 묶어 그대로 위임한다 (size 를 절삭하지 않는다)") {
                result.andExpect(status().isOk)
                verify(exactly = 1) {
                    searchGoodsCatalogUseCase.execute(
                        InternalGoodsCatalogCriteria(keyword = "저지", sellerType = SellerType.B2B, page = 2, size = 5),
                    )
                }
            }
        }
    }

    Given("파사드가 넓은 창을 요청하면 (size=300)") {
        val searchGoodsCatalogUseCase = mockk<SearchGoodsCatalogUseCase>()
        every { searchGoodsCatalogUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(searchGoodsCatalogUseCase)

        When("GET /internal/catalog/goods?size=300 요청 시") {
            mockMvc.perform(get("/internal/catalog/goods").param("size", "300"))

            Then("size 를 100 으로 절삭하지 않고 그대로 전달한다 (절삭하면 page>=1 결과가 유실된다)") {
                verify(exactly = 1) {
                    searchGoodsCatalogUseCase.execute(
                        InternalGoodsCatalogCriteria(keyword = null, sellerType = null, page = 0, size = 300),
                    )
                }
            }
        }
    }

    Given("검색 결과가 0건이면") {
        val searchGoodsCatalogUseCase = mockk<SearchGoodsCatalogUseCase>()
        every { searchGoodsCatalogUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(searchGoodsCatalogUseCase)

        When("GET /internal/catalog/goods 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/goods"))

            Then("200 과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
