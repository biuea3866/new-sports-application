package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.TicketOrderDetailResponse
import com.sportsapp.application.ticketing.dto.TicketOrderResponse
import com.sportsapp.application.ticketing.usecase.GetTicketOrderUseCase
import com.sportsapp.application.ticketing.usecase.PurchaseTicketsUseCase
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.exception.UnauthorizedTicketOrderAccessException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/** AUTH-04 — 티켓 주문 생성은 `@AuthenticationPrincipal UserPrincipal`(non-null)로 식별한다. */
class TicketOrderApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        purchaseTicketsUseCase: PurchaseTicketsUseCase = mockk(),
        getTicketOrderUseCase: GetTicketOrderUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        TicketOrderApiController(purchaseTicketsUseCase, getTicketOrderUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("로그인한 사용자의 티켓 구매 요청") {
        val purchaseTicketsUseCase = mockk<PurchaseTicketsUseCase>()
        every {
            purchaseTicketsUseCase.execute(match { it.userId == TEST_USER_ID && it.lockId == "lock-1" })
        } returns TicketOrderResponse(ticketOrderId = 1L, status = OrderStatus.PENDING)
        val mockMvc = buildMockMvc(purchaseTicketsUseCase = purchaseTicketsUseCase)

        When("POST /ticket-orders 요청 시") {
            val body = """{"lockId":"lock-1","method":"${PaymentMethod.CREDIT_CARD}","currency":"KRW"}"""
            val result = mockMvc.perform(
                post("/ticket-orders")
                    .header("Idempotency-Key", "idem-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            )

            Then("principal.id 로 주문이 생성되고 202를 반환한다") {
                result.andExpect(status().isAccepted)
                    .andExpect(jsonPath("$.ticketOrderId").value(1))
                verify(exactly = 1) { purchaseTicketsUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 소유의 티켓 주문 조회 요청") {
        val getTicketOrderUseCase = mockk<GetTicketOrderUseCase>()
        every { getTicketOrderUseCase.execute(1L, TEST_USER_ID) } returns TicketOrderDetailResponse(
            ticketOrderId = 1L,
            status = OrderStatus.PENDING,
            eventId = 10L,
            eventTitle = "테스트 콘서트",
            paymentId = null,
            createdAt = ZonedDateTime.now(),
        )
        val mockMvc = buildMockMvc(getTicketOrderUseCase = getTicketOrderUseCase)

        When("GET /ticket-orders/1 요청 시") {
            val result = mockMvc.perform(get("/ticket-orders/1"))

            Then("principal.id 가 requesterId 로 전달되고 200을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.ticketOrderId").value(1))
                verify(exactly = 1) { getTicketOrderUseCase.execute(1L, TEST_USER_ID) }
            }
        }
    }

    Given("타인 소유의 티켓 주문 조회 요청") {
        val getTicketOrderUseCase = mockk<GetTicketOrderUseCase>()
        every {
            getTicketOrderUseCase.execute(2L, TEST_USER_ID)
        } throws UnauthorizedTicketOrderAccessException(2L)
        val mockMvc = buildMockMvc(getTicketOrderUseCase = getTicketOrderUseCase)

        When("GET /ticket-orders/2 요청 시 (본인 소유가 아님)") {
            val result = mockMvc.perform(get("/ticket-orders/2"))

            Then("403 Forbidden을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("TICKET_ORDER_ACCESS_DENIED"))
            }
        }
    }
})
