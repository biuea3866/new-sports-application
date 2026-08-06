package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.InternalTicketingOrderHistoryItemResponse
import com.sportsapp.application.ticketing.usecase.FindTicketingOrderHistoryUseCase
import com.sportsapp.domain.ticketing.entity.OrderStatus
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

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08)의 ticketing 원격 공급 엔드포인트 계약 검증 (S2-03).
 *
 * 좌석은 **원본 필드로 실어 보내는 것**이 계약이라 JSON 레벨에서 그 구조를 고정한다 — 문자열로
 * 조합해 보내면 모바일 `formatSeatDescription` 과 서식이 갈리는 두 번째 포맷터가 생긴다.
 */
class InternalTicketingOrderApiControllerTest : BehaviorSpec({

    fun buildMockMvc(findTicketingOrderHistoryUseCase: FindTicketingOrderHistoryUseCase) = MockMvcBuilders
        .standaloneSetup(InternalTicketingOrderApiController(findTicketingOrderHistoryUseCase))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(productionEquivalentJsonConverter())
        .build()

    fun orderHistoryItem(
        sourceId: Long = 21L,
        paymentId: Long? = 801L,
        seats: List<InternalTicketingOrderHistoryItemResponse.SeatResponse> = listOf(
            InternalTicketingOrderHistoryItemResponse.SeatResponse(section = "R", rowNo = "1", seatNo = "R-01"),
            InternalTicketingOrderHistoryItemResponse.SeatResponse(section = "R", rowNo = "1", seatNo = "R-02"),
        ),
    ) = InternalTicketingOrderHistoryItemResponse(
        sourceId = sourceId,
        title = "농구 결승전",
        status = OrderStatus.CONFIRMED,
        paymentId = paymentId,
        createdAt = ZonedDateTime.now(),
        amount = BigDecimal("88000"),
        seats = seats,
    )

    Given("신원 헤더로 본인 티켓 주문 이력을 조회하면") {
        val findTicketingOrderHistoryUseCase = mockk<FindTicketingOrderHistoryUseCase>()
        every { findTicketingOrderHistoryUseCase.execute(7L) } returns listOf(orderHistoryItem())
        val mockMvc = buildMockMvc(findTicketingOrderHistoryUseCase)

        When("GET /internal/order-history/ticketing 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/ticketing").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            Then("200 과 함께 그 사용자의 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(21))
            }

            Then("소비자(edge OrderHistoryItem)가 요구하는 계약 필드가 응답 JSON 에 모두 실린다") {
                result.andExpect(jsonPath("$[0].title").value("농구 결승전"))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$[0].paymentId").value(801))
                    .andExpect(jsonPath("$[0].amount").value(88000))
                    .andExpect(jsonPath("$[0].createdAt").exists())
            }

            Then("좌석을 원본 필드(section·rowNo·seatNo) 구조로 실어 보낸다 (문자열 미조합)") {
                result.andExpect(jsonPath("$[0].seats.length()").value(2))
                    .andExpect(jsonPath("$[0].seats[0].section").value("R"))
                    .andExpect(jsonPath("$[0].seats[0].rowNo").value("1"))
                    .andExpect(jsonPath("$[0].seats[0].seatNo").value("R-01"))
                    .andExpect(jsonPath("$[0].seats[1].seatNo").value("R-02"))
            }

            Then("파사드가 만드는 필드(orderType·detailPath)는 응답에 없다") {
                result.andExpect(jsonPath("$[0].orderType").doesNotExist())
                    .andExpect(jsonPath("$[0].detailPath").doesNotExist())
            }
        }
    }

    Given("좌석 정보가 없는 주문이면") {
        val findTicketingOrderHistoryUseCase = mockk<FindTicketingOrderHistoryUseCase>()
        every { findTicketingOrderHistoryUseCase.execute(7L) } returns
            listOf(orderHistoryItem(sourceId = 22L, paymentId = null, seats = emptyList()))
        val mockMvc = buildMockMvc(findTicketingOrderHistoryUseCase)

        When("GET /internal/order-history/ticketing 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/ticketing").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            Then("좌석을 빈 배열로 보낸다 — null 판정(구분 정보 없음)은 파사드가 한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].seats").isArray)
                    .andExpect(jsonPath("$[0].seats.length()").value(0))
            }

            // PR #385 계약 — 전역 ObjectMapper 는 null 을 키와 함께 남긴다.
            Then("결제 전 주문의 paymentId 는 키를 유지한 채 null 이다") {
                result.andExpect(jsonPath("$[0].paymentId").hasJsonPath())
                    .andExpect(jsonPath("$[0].paymentId").value(nullValue()))
            }
        }
    }

    Given("신원 헤더가 없으면") {
        val findTicketingOrderHistoryUseCase = mockk<FindTicketingOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(findTicketingOrderHistoryUseCase)

        When("GET /internal/order-history/ticketing 요청 시") {
            val result = mockMvc.perform(get("/internal/order-history/ticketing"))

            Then("400 으로 거부하고 조회를 수행하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { findTicketingOrderHistoryUseCase.execute(any()) }
            }
        }
    }

    Given("신원 헤더 값이 사용자 PK 로 해석되지 않으면") {
        val findTicketingOrderHistoryUseCase = mockk<FindTicketingOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(findTicketingOrderHistoryUseCase)

        When("GET /internal/order-history/ticketing 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/ticketing").header(INTERNAL_AUTH_SUBJECT_HEADER, "not-a-user-id"),
            )

            Then("400 으로 거부하고 받은 헤더 값을 응답에 되돌려주지 않는다") {
                result.andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.detail").value("Invalid internal identity header"))
                verify(exactly = 0) { findTicketingOrderHistoryUseCase.execute(any()) }
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val findTicketingOrderHistoryUseCase = mockk<FindTicketingOrderHistoryUseCase>()
        every { findTicketingOrderHistoryUseCase.execute(any()) } returns emptyList()
        val mockMvc = buildMockMvc(findTicketingOrderHistoryUseCase)

        When("GET /internal/order-history/ticketing 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/ticketing").header(INTERNAL_AUTH_SUBJECT_HEADER, "7"),
            )

            Then("200 과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
