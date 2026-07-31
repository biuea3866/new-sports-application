package com.sportsapp.presentation.ticketing.worker

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class TicketingPaymentEventWorkerTest : BehaviorSpec({

    val confirmUseCase = mockk<ConfirmTicketingPaymentUseCase>()
    val cancelUseCase = mockk<CancelTicketingPaymentUseCase>()
    val meterRegistry = SimpleMeterRegistry()
    val worker = TicketingPaymentEventWorker(confirmUseCase, cancelUseCase, meterRegistry)
    justRun { confirmUseCase.execute(any(), any()) }
    justRun { cancelUseCase.execute(any()) }

    Given("결제 이벤트 워커") {
        When("TICKETING 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L, recipientUserId = 1L, amount = 0L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(30L, 300L) }
            }
        }

        When("TICKETING 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(30L) }
            }
        }

        When("다른 타입(RECRUITMENT) 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 400L, orderType = OrderType.RECRUITMENT, orderId = 40L, recipientUserId = 1L, amount = 0L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(40L, 400L) }
            }
        }
    }

    Given("종결 상태(CANCELLED) 주문에 결제 확정 이벤트가 도달한 좁은 레이스 상황") {
        val raceConfirmUseCase = mockk<ConfirmTicketingPaymentUseCase>()
        val raceCancelUseCase = mockk<CancelTicketingPaymentUseCase>()
        val raceMeterRegistry = SimpleMeterRegistry()
        val raceWorker = TicketingPaymentEventWorker(raceConfirmUseCase, raceCancelUseCase, raceMeterRegistry)
        every { raceConfirmUseCase.execute(50L, 500L) } throws InvalidOrderStateException("Cannot transit from CANCELLED to CONFIRMED")

        When("확정 이벤트를 수신하면") {
            val event = PaymentEvent.Confirmed(paymentId = 500L, orderType = OrderType.TICKETING, orderId = 50L, recipientUserId = 1L, amount = 0L)

            Then("컨슈머가 죽지 않고 ticketing_confirm_after_terminal_total 카운터가 증가한다") {
                shouldNotThrowAny { raceWorker.consume(event) }
                raceMeterRegistry.counter("ticketing_confirm_after_terminal_total").count() shouldBe 1.0
            }
        }
    }
})
