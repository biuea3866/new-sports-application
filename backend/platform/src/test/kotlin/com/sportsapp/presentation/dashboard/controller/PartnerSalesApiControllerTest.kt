package com.sportsapp.presentation.dashboard.controller

import com.sportsapp.application.dashboard.dto.ListPartnerSalesCommand
import com.sportsapp.application.dashboard.dto.ListPartnerSalesResult
import com.sportsapp.application.dashboard.dto.PartnerSaleResult
import com.sportsapp.application.dashboard.usecase.ListPartnerSalesUseCase
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import sportsapp.testkit.presentation.support.fixedPrincipalResolver
import java.math.BigDecimal
import java.time.ZonedDateTime

private const val PARTNER_USER_ID = 69L

/**
 * 파트너 매출 API — 조회 대상 판매자를 **서버가 인증 주체로 고정**하는지 검증한다(IDOR 차단).
 * 클라이언트가 ownerUserId를 지정할 수 있으면 남의 매출을 조회할 수 있다.
 */
class PartnerSalesApiControllerTest : BehaviorSpec({

    fun buildMockMvc(useCase: ListPartnerSalesUseCase, userId: Long = PARTNER_USER_ID) =
        MockMvcBuilders.standaloneSetup(PartnerSalesApiController(useCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
            .build()

    fun buildSale() = PartnerSaleResult(
        paymentId = 16L,
        orderType = OrderType.BOOKING,
        orderId = 1L,
        sellerAmount = BigDecimal("25000.00"),
        method = "CREDIT_CARD",
        provider = "TOSS",
        status = PaymentStatus.COMPLETED,
        paidAt = ZonedDateTime.now(),
        pgTransactionId = "tid-16",
    )

    Given("판매 매출이 있는 파트너") {
        val useCase = mockk<ListPartnerSalesUseCase>()
        val commandSlot = slot<ListPartnerSalesCommand>()
        every { useCase.execute(capture(commandSlot)) } returns ListPartnerSalesResult(
            sales = listOf(buildSale()),
            totalElements = 1L,
            totalPages = 1,
            page = 0,
            size = 20,
        )
        val mockMvc = buildMockMvc(useCase)

        When("매출 내역을 조회하면") {
            val response = mockMvc.perform(get("/api/operator/dashboard/sales"))

            Then("200과 매출 목록이 반환된다") {
                response.andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(1))
                        .andExpect(jsonPath("$.sales[0].paymentId").value(16))
            }

            Then("내 매출만 실리고 결제 총액은 노출되지 않는다") {
                response.andExpect(jsonPath("$.sales[0].sellerAmount").value(25000.00))
                        .andExpect(jsonPath("$.sales[0].amount").doesNotExist())
            }

            Then("인증된 파트너 id가 판매자 스코프로 전달된다") {
                commandSlot.captured.ownerUserId shouldBe PARTNER_USER_ID
            }
        }
    }

    Given("상태·기간 필터를 지정했을 때") {
        val useCase = mockk<ListPartnerSalesUseCase>()
        val commandSlot = slot<ListPartnerSalesCommand>()
        every { useCase.execute(capture(commandSlot)) } returns ListPartnerSalesResult(
            sales = emptyList(),
            totalElements = 0L,
            totalPages = 0,
            page = 0,
            size = 20,
        )
        val mockMvc = buildMockMvc(useCase)

        When("쿼리 파라미터로 조회하면") {
            mockMvc.perform(get("/api/operator/dashboard/sales?status=COMPLETED&page=2&size=5"))

            Then("상태·페이지가 그대로 커맨드에 담긴다") {
                commandSlot.captured.status shouldBe PaymentStatus.COMPLETED
                commandSlot.captured.pageable.pageNumber shouldBe 2
                commandSlot.captured.pageable.pageSize shouldBe 5
            }
        }
    }

    Given("판매 매출이 없는 파트너") {
        val useCase = mockk<ListPartnerSalesUseCase>()
        every { useCase.execute(any()) } returns ListPartnerSalesResult(
            sales = emptyList(),
            totalElements = 0L,
            totalPages = 0,
            page = 0,
            size = 20,
        )
        val mockMvc = buildMockMvc(useCase)

        When("매출 내역을 조회하면") {
            val response = mockMvc.perform(get("/api/operator/dashboard/sales"))

            Then("200과 빈 목록이 반환된다") {
                response.andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(0))
                        .andExpect(jsonPath("$.sales").isEmpty)
            }
        }
    }
})
