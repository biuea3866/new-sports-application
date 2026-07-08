package com.sportsapp.presentation.booking.worker

import com.sportsapp.application.booking.usecase.CancelBookingPaymentUseCase
import com.sportsapp.application.booking.usecase.ConfirmBookingPaymentUseCase
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.vo.OrderType
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class BookingPaymentEventWorkerTest : BehaviorSpec({

    val confirmUseCase = mockk<ConfirmBookingPaymentUseCase>()
    val cancelUseCase = mockk<CancelBookingPaymentUseCase>()
    val worker = BookingPaymentEventWorker(confirmUseCase, cancelUseCase)
    justRun { confirmUseCase.execute(any(), any()) }
    justRun { cancelUseCase.execute(any()) }

    Given("결제 이벤트 워커") {
        When("BOOKING 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("자기 확정 UseCase 에 위임한다") {
                verify(exactly = 1) { confirmUseCase.execute(10L, 100L) }
            }
        }

        When("BOOKING 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 100L, orderType = OrderType.BOOKING, orderId = 10L))

            Then("자기 취소 UseCase 에 위임한다") {
                verify(exactly = 1) { cancelUseCase.execute(10L) }
            }
        }

        When("BOOKING 취소 이벤트를 중복 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 101L, orderType = OrderType.BOOKING, orderId = 11L))
            worker.consume(PaymentEvent.Cancelled(paymentId = 101L, orderType = OrderType.BOOKING, orderId = 11L))

            Then("매 수신마다 취소 UseCase 에 위임하고 멱등은 하류가 보장한다") {
                verify(exactly = 2) { cancelUseCase.execute(11L) }
            }
        }

        When("다른 타입(GOODS) 확정 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Confirmed(paymentId = 200L, orderType = OrderType.GOODS, orderId = 20L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { confirmUseCase.execute(20L, 200L) }
            }
        }

        When("다른 타입(TICKETING) 취소 이벤트를 수신하면") {
            worker.consume(PaymentEvent.Cancelled(paymentId = 300L, orderType = OrderType.TICKETING, orderId = 30L))

            Then("무시하고 어떤 UseCase 도 호출하지 않는다") {
                verify(exactly = 0) { cancelUseCase.execute(30L) }
            }
        }
    }
})
