package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentCancelledEvent
import com.sportsapp.domain.payment.event.PaymentConfirmedEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class GoodsPaymentEventWorkerTest : BehaviorSpec({

    Given("확정 워커") {
        val confirmUseCase = mockk<ConfirmGoodsPaymentUseCase>()
        val worker = PaymentConfirmedEventWorker(confirmUseCase)
        justRun { confirmUseCase.execute(any(), any()) }

        When("GOODS 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(20L, 200L) }
            }
        }

        When("다른 타입(BOOKING) 확정 이벤트를 수신하면") {
            worker.consume(PaymentConfirmedEvent(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("무시하고 UseCase 를 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(10L, 100L) }
            }
        }
    }

    Given("취소 워커") {
        val cancelUseCase = mockk<CancelGoodsPaymentUseCase>()
        val worker = PaymentCancelledEventWorker(cancelUseCase)
        justRun { cancelUseCase.execute(any()) }

        When("GOODS 취소 이벤트를 수신하면") {
            worker.consume(PaymentCancelledEvent(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(20L) }
            }
        }
    }
})
