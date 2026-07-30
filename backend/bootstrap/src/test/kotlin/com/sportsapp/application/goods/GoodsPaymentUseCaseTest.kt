package com.sportsapp.application.goods

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class GoodsPaymentUseCaseTest : BehaviorSpec({

    Given("확정 UseCase") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = ConfirmGoodsPaymentUseCase(goodsDomainService)
        every { goodsDomainService.markPaid(20L, 200L) } returns mockk<GoodsOrder>()

        When("execute(orderId, paymentId) 를 호출하면") {
            useCase.execute(20L, 200L)

            Then("GoodsDomainService.markPaid 에 위임한다") {
                verify(exactly = 1) { goodsDomainService.markPaid(20L, 200L) }
            }
        }
    }

    Given("취소 UseCase") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val useCase = CancelGoodsPaymentUseCase(goodsDomainService)
        justRun { goodsDomainService.cancelPendingOrder(any()) }

        When("동일 이벤트를 2회 수신하면") {
            useCase.execute(21L)
            useCase.execute(21L)

            Then("매 수신마다 취소에 위임하고 재고 이중 복원 방지는 도메인이 보장한다") {
                verify(exactly = 2) { goodsDomainService.cancelPendingOrder(21L) }
            }
        }
    }
})
