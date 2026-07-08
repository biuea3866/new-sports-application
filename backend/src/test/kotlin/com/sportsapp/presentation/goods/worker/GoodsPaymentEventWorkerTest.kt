package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class GoodsPaymentEventWorkerTest : BehaviorSpec({

    val confirmUseCase = mockk<ConfirmGoodsPaymentUseCase>()
    val cancelUseCase = mockk<CancelGoodsPaymentUseCase>()
    val worker = GoodsPaymentEventWorker(confirmUseCase, cancelUseCase)
    justRun { confirmUseCase.execute(any(), any()) }
    justRun { cancelUseCase.execute(any()) }

    Given("결제 이벤트 워커") {
        When("GOODS 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(20L, 200L) }
            }
        }

        When("GOODS 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(20L) }
            }
        }

        When("다른 타입(BOOKING) 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(10L, 100L) }
            }
        }
    }
})
