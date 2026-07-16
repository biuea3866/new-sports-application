package com.sportsapp.presentation.order.controller

import com.sportsapp.application.order.GetOrderHistoryUseCase
import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryItem
import com.sportsapp.application.order.dto.OrderHistoryResponse
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.ZoneOffset
import java.time.ZonedDateTime

private fun buildMockMvc(
    getOrderHistoryUseCase: GetOrderHistoryUseCase,
    principal: UserPrincipal = UserPrincipal(id = 1L, email = "user@test.com", roles = listOf("USER")),
) = MockMvcBuilders.standaloneSetup(OrderHistoryApiController(getOrderHistoryUseCase))
    .setCustomArgumentResolvers(fixedPrincipalResolver(principal.id, principal.email, principal.roles))
    .build()

class OrderHistoryApiControllerTest : BehaviorSpec({

    Given("JWT 인증된 사용자가 조건 없이 조회할 때") {
        val getOrderHistoryUseCase = mockk<GetOrderHistoryUseCase>()
        val criteriaSlot = slot<OrderHistoryCriteria>()
        val response = OrderHistoryResponse(
            items = listOf(
                OrderHistoryItem(
                    orderType = OrderType.GOODS,
                    sourceId = 20L,
                    title = "요가매트 프리미엄 외 1건",
                    status = "SHIPPED",
                    paymentId = 200L,
                    detailPath = "/goods-orders/20",
                    createdAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC),
                ),
            ),
            page = 0,
            size = 20,
            failedDomains = emptyList(),
        )
        every { getOrderHistoryUseCase.execute(1L, capture(criteriaSlot)) } returns response
        val mockMvc = buildMockMvc(getOrderHistoryUseCase)

        When("GET /api/orders 요청 시") {
            val result = mockMvc.perform(get("/api/orders"))

            Then("200과 함께 조합된 주문내역을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.items[0].orderType").value("GOODS"))
                    .andExpect(jsonPath("$.items[0].title").value("요가매트 프리미엄 외 1건"))
                    .andExpect(jsonPath("$.failedDomains").isArray)
                verify(exactly = 1) { getOrderHistoryUseCase.execute(1L, any()) }
                criteriaSlot.captured shouldBe OrderHistoryCriteria(orderType = null, status = null, page = 0, size = 20)
            }
        }
    }

    Given("orderType·status·page·size 쿼리 파라미터가 주어졌을 때") {
        val getOrderHistoryUseCase = mockk<GetOrderHistoryUseCase>()
        val criteriaSlot = slot<OrderHistoryCriteria>()
        val response = OrderHistoryResponse(items = emptyList(), page = 1, size = 5, failedDomains = emptyList())
        every { getOrderHistoryUseCase.execute(1L, capture(criteriaSlot)) } returns response
        val mockMvc = buildMockMvc(getOrderHistoryUseCase)

        When("GET /api/orders?orderType=TICKETING&status=CONFIRMED&page=1&size=5 요청 시") {
            val result = mockMvc.perform(
                get("/api/orders")
                    .param("orderType", "TICKETING")
                    .param("status", "CONFIRMED")
                    .param("page", "1")
                    .param("size", "5"),
            )

            Then("파라미터가 그대로 OrderHistoryCriteria로 전달된다") {
                result.andExpect(status().isOk)
                criteriaSlot.captured shouldBe OrderHistoryCriteria(orderType = OrderType.TICKETING, status = "CONFIRMED", page = 1, size = 5)
            }
        }
    }
})
