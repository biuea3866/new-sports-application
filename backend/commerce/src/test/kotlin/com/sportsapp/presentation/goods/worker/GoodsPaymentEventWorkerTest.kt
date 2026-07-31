package com.sportsapp.presentation.goods.worker

import com.sportsapp.application.goods.usecase.CancelGoodsPaymentUseCase
import com.sportsapp.application.goods.usecase.ConfirmGoodsPaymentUseCase
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class GoodsPaymentEventWorkerTest : BehaviorSpec({

    Given("결제 이벤트 워커") {
        val confirmUseCase = mockk<ConfirmGoodsPaymentUseCase>()
        val cancelUseCase = mockk<CancelGoodsPaymentUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val worker = GoodsPaymentEventWorker(confirmUseCase, cancelUseCase, meterRegistry)
        justRun { confirmUseCase.execute(any(), any()) }
        justRun { cancelUseCase.execute(any()) }

        When("GOODS 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L, recipientUserId = 1L, amount = 0L))

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
            worker.consume(PaymentEvent.Confirmed(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L, recipientUserId = 1L, amount = 0L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(10L, 100L) }
            }
        }
    }

    Given("W1-11a 만료 스위퍼가 먼저 CANCELLED로 전이시킨 주문에 결제 확정 이벤트가 뒤늦게 도착할 때 (핵심 회귀 — 컨슈머 생존 + 경보)") {
        val confirmUseCase = mockk<ConfirmGoodsPaymentUseCase>()
        val cancelUseCase = mockk<CancelGoodsPaymentUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val worker = GoodsPaymentEventWorker(confirmUseCase, cancelUseCase, meterRegistry)
        every { confirmUseCase.execute(30L, 300L) } throws InvalidGoodsOrderStateException(GoodsOrderStatus.CANCELLED, GoodsOrderStatus.CONFIRMED)

        When("GOODS 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 300L, orderType = OrderType.GOODS, orderId = 30L, recipientUserId = 1L, amount = 0L))

            Then("예외로 컨슈머를 죽이지 않고 경보 지표(goods_confirm_after_terminal_total)가 1 증가한다") {
                meterRegistry.counter("goods_confirm_after_terminal_total").count() shouldBe 1.0
            }
        }
    }
})
