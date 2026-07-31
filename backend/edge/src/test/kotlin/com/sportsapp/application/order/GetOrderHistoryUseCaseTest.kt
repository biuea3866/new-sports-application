package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryResponse
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class GetOrderHistoryUseCaseTest : BehaviorSpec({

    Given("userId와 조회 조건이 주어졌을 때") {
        val orderCompositionService = mockk<OrderCompositionService>()
        val useCase = GetOrderHistoryUseCase(orderCompositionService)
        val userId = 7L
        val criteria = OrderHistoryCriteria(orderType = null, status = null, page = 0, size = 20)
        val response = OrderHistoryResponse(items = emptyList(), page = 0, size = 20, failedDomains = emptyList())

        every { orderCompositionService.history(userId, criteria) } returns response

        When("execute를 호출하면") {
            val result = useCase.execute(userId, criteria)

            Then("OrderCompositionService에 그대로 위임한 결과를 반환한다") {
                result shouldBe response
                verify(exactly = 1) { orderCompositionService.history(userId, criteria) }
            }
        }
    }
})
