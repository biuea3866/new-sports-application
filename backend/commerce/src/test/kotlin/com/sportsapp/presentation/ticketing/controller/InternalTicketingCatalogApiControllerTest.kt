package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogCriteria
import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogItemResponse
import com.sportsapp.application.ticketing.usecase.SearchTicketingCatalogUseCase
import com.sportsapp.domain.ticketing.entity.EventStatus
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
 * catalog 통합검색(BE-07)의 ticketing 원격 공급 엔드포인트 계약 검증 (S2-03).
 *
 * 경기장명·시작 일시는 같은 제목의 경기를 사용자가 구분하는 **실데이터**라 응답에 실려야 하고,
 * 최저 좌석가는 좌석 미등록 경기에서 null 이어야 한다(0 으로 방어하면 무료 경기와 구분되지 않는다).
 */
class InternalTicketingCatalogApiControllerTest : BehaviorSpec({

    fun buildMockMvc(searchTicketingCatalogUseCase: SearchTicketingCatalogUseCase) = MockMvcBuilders
        .standaloneSetup(InternalTicketingCatalogApiController(searchTicketingCatalogUseCase))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(productionEquivalentJsonConverter())
        .build()

    fun catalogItem(sourceId: Long = 5L, price: BigDecimal? = BigDecimal("44000")) =
        InternalTicketingCatalogItemResponse(
            sourceId = sourceId,
            title = "농구 결승전",
            price = price,
            status = EventStatus.OPEN,
            createdAt = ZonedDateTime.now(),
            locationName = "잠실 실내체육관",
            scheduledAt = ZonedDateTime.now().plusDays(7),
        )

    Given("신원 헤더 없이 경기 카탈로그를 조회하면 (공개 조회)") {
        val searchTicketingCatalogUseCase = mockk<SearchTicketingCatalogUseCase>()
        every {
            searchTicketingCatalogUseCase.execute(
                InternalTicketingCatalogCriteria(keyword = null, page = 0, size = 20),
            )
        } returns listOf(catalogItem())
        val mockMvc = buildMockMvc(searchTicketingCatalogUseCase)

        When("GET /internal/catalog/ticketing 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/ticketing"))

            Then("200 과 함께 계약 필드를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(5))
                    .andExpect(jsonPath("$[0].title").value("농구 결승전"))
                    .andExpect(jsonPath("$[0].price").value(44000))
                    .andExpect(jsonPath("$[0].status").value("OPEN"))
                    .andExpect(jsonPath("$[0].createdAt").exists())
            }

            Then("경기장명·시작 일시를 함께 실어 보낸다 — 같은 제목의 경기를 구분하는 근거다") {
                result.andExpect(jsonPath("$[0].locationName").value("잠실 실내체육관"))
                    .andExpect(jsonPath("$[0].scheduledAt").exists())
            }

            Then("파사드가 만드는 필드(itemType·sellerType·detailPath)는 응답에 없다") {
                result.andExpect(jsonPath("$[0].itemType").doesNotExist())
                    .andExpect(jsonPath("$[0].sellerType").doesNotExist())
                    .andExpect(jsonPath("$[0].detailPath").doesNotExist())
            }
        }
    }

    Given("좌석이 등록되지 않은 경기이면") {
        val searchTicketingCatalogUseCase = mockk<SearchTicketingCatalogUseCase>()
        every { searchTicketingCatalogUseCase.execute(any()) } returns listOf(catalogItem(sourceId = 6L, price = null))
        val mockMvc = buildMockMvc(searchTicketingCatalogUseCase)

        When("GET /internal/catalog/ticketing 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/ticketing"))

            // PR #385 계약 — 전역 ObjectMapper 는 null 을 키와 함께 남긴다.
            Then("가격을 키를 유지한 채 null 로 보낸다 (0 으로 방어하지 않는다)") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].price").hasJsonPath())
                    .andExpect(jsonPath("$[0].price").value(nullValue()))
            }
        }
    }

    Given("파사드가 넓은 창을 요청하면 (size=300)") {
        val searchTicketingCatalogUseCase = mockk<SearchTicketingCatalogUseCase>()
        every { searchTicketingCatalogUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(searchTicketingCatalogUseCase)

        When("GET /internal/catalog/ticketing?keyword=결승&size=300 요청 시") {
            mockMvc.perform(get("/internal/catalog/ticketing").param("keyword", "결승").param("size", "300"))

            Then("size 를 절삭하지 않고 조건을 값 객체로 묶어 위임한다") {
                verify(exactly = 1) {
                    searchTicketingCatalogUseCase.execute(
                        InternalTicketingCatalogCriteria(keyword = "결승", page = 0, size = 300),
                    )
                }
            }
        }
    }

    Given("검색 결과가 0건이면") {
        val searchTicketingCatalogUseCase = mockk<SearchTicketingCatalogUseCase>()
        every { searchTicketingCatalogUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(searchTicketingCatalogUseCase)

        When("GET /internal/catalog/ticketing 요청 시") {
            val result = mockMvc.perform(get("/internal/catalog/ticketing"))

            Then("200 과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
