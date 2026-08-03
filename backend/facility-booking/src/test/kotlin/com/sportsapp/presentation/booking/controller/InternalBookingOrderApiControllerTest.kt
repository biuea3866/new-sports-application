package com.sportsapp.presentation.booking.controller

import com.sportsapp.application.booking.dto.InternalBookingOrderHistoryItemResponse
import com.sportsapp.application.booking.usecase.FindBookingOrderHistoryUseCase
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * edge 통합 주문내역(BE-08)의 OrderHistoryGateway.findBookingOrders 원격 구현(2단계)이 호출할
 * 공급자 엔드포인트 계약 검증 (S2-04).
 */
class InternalBookingOrderApiControllerTest : BehaviorSpec({

    fun buildMockMvc(findBookingOrderHistoryUseCase: FindBookingOrderHistoryUseCase) = MockMvcBuilders.standaloneSetup(
        InternalBookingOrderApiController(findBookingOrderHistoryUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    fun orderHistoryItem(bookingId: Long = 1L, status: String = "CONFIRMED") = InternalBookingOrderHistoryItemResponse(
        bookingId = bookingId,
        title = "8월 3일 10:00~11:00",
        status = status,
        paymentId = 100L,
        createdAt = ZonedDateTime.now(),
    )

    Given("X-Internal-Auth-Subject 헤더로 사용자를 식별해 예약 이력을 요청하면") {
        val findBookingOrderHistoryUseCase = mockk<FindBookingOrderHistoryUseCase>()
        every { findBookingOrderHistoryUseCase.execute(1L) } returns listOf(orderHistoryItem())
        val mockMvc = buildMockMvc(findBookingOrderHistoryUseCase)

        When("GET /internal/order-history/bookings 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/bookings").header(INTERNAL_AUTH_SUBJECT_HEADER, "1"),
            )

            Then("200과 함께 그 사용자의 계약 필드로 정규화된 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].bookingId").value(1))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                verify { findBookingOrderHistoryUseCase.execute(1L) }
            }

            Then("Booking 엔티티 필드(slotId·userId)는 노출되지 않는다") {
                result.andExpect(jsonPath("$[0].slotId").doesNotExist())
                    .andExpect(jsonPath("$[0].userId").doesNotExist())
            }
        }
    }

    Given("X-Internal-Auth-Subject 헤더가 없으면") {
        val findBookingOrderHistoryUseCase = mockk<FindBookingOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(findBookingOrderHistoryUseCase)

        When("GET /internal/order-history/bookings 요청 시") {
            val result = mockMvc.perform(get("/internal/order-history/bookings"))

            Then("400으로 거부한다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { findBookingOrderHistoryUseCase.execute(any()) }
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val findBookingOrderHistoryUseCase = mockk<FindBookingOrderHistoryUseCase>()
        every { findBookingOrderHistoryUseCase.execute(1L) } returns emptyList()
        val mockMvc = buildMockMvc(findBookingOrderHistoryUseCase)

        When("GET /internal/order-history/bookings 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/bookings").header(INTERNAL_AUTH_SUBJECT_HEADER, "1"),
            )

            Then("200과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})
