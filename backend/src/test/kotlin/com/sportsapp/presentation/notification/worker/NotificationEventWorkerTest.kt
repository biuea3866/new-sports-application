package com.sportsapp.presentation.notification.worker

import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.application.notification.usecase.EnqueueNotificationUseCase
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.ticketing.event.TicketEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class NotificationEventWorkerTest : BehaviorSpec({

    fun buildWorker(captured: MutableList<EnqueueNotificationCommand>): NotificationEventWorker {
        val useCase = mockk<EnqueueNotificationUseCase>()
        every { useCase.execute(any()) } answers { captured.add(firstArg()); Unit }
        return NotificationEventWorker(useCase)
    }

    Given("PaymentEvent.Confirmed 를 수신하면") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = PaymentEvent.Confirmed(
            paymentId = 10L,
            orderType = OrderType.BOOKING,
            orderId = 100L,
            recipientUserId = 900L,
            amount = 30000L,
        )

        When("consumePayment 를 호출하면") {
            worker.consumePayment(event)

            Then("IN_APP 과 PUSH 두 건의 결제 완료 알림 command 를 발행한다") {
                captured.size shouldBe 2
                captured.map { it.channel }.shouldContainExactlyInAnyOrder(
                    NotificationChannel.IN_APP,
                    NotificationChannel.PUSH,
                )
                captured.forEach { command ->
                    command.templateId shouldBe "payment-completed"
                    command.recipientUserId shouldBe 900L
                    command.payload.data["amount"] shouldBe "30000"
                }
                val inApp = captured.single { it.channel == NotificationChannel.IN_APP }
                val push = captured.single { it.channel == NotificationChannel.PUSH }
                inApp.eventId shouldBe event.eventId
                push.eventId shouldBe "${event.eventId}:push"
            }
        }
    }

    Given("PaymentEvent.Cancelled 를 수신하면") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = PaymentEvent.Cancelled(paymentId = 11L, orderType = OrderType.GOODS, orderId = 200L)

        When("consumePayment 를 호출하면") {
            worker.consumePayment(event)

            Then("어떤 알림 command 도 발행하지 않는다 (no-op)") {
                captured.size shouldBe 0
            }
        }
    }

    Given("BookingEvent.Confirmed 를 수신하면") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = BookingEvent.Confirmed(bookingId = 20L, paymentId = 55L, recipientUserId = 901L)

        When("consumeBooking 을 호출하면") {
            worker.consumeBooking(event)

            Then("IN_APP 과 PUSH 두 건의 예약 확정 알림 command 를 발행한다") {
                captured.size shouldBe 2
                captured.forEach { command ->
                    command.templateId shouldBe "booking-confirmed"
                    command.recipientUserId shouldBe 901L
                }
                captured.single { it.channel == NotificationChannel.IN_APP }.eventId shouldBe event.eventId
                captured.single { it.channel == NotificationChannel.PUSH }.eventId shouldBe "${event.eventId}:push"
            }
        }
    }

    Given("TicketEvent.Issued 를 수신하면") {
        val captured = mutableListOf<EnqueueNotificationCommand>()
        val worker = buildWorker(captured)
        val event = TicketEvent.Issued(ticketOrderId = 30L, recipientUserId = 902L, eventTitle = "월드컵 결승")

        When("consumeTicket 을 호출하면") {
            worker.consumeTicket(event)

            Then("IN_APP 과 PUSH 두 건의 발권 알림 command 를 발행한다") {
                captured.size shouldBe 2
                captured.forEach { command ->
                    command.templateId shouldBe "ticket-issued"
                    command.recipientUserId shouldBe 902L
                    command.payload.data["eventTitle"] shouldBe "월드컵 결승"
                }
                captured.single { it.channel == NotificationChannel.IN_APP }.eventId shouldBe event.eventId
                captured.single { it.channel == NotificationChannel.PUSH }.eventId shouldBe "${event.eventId}:push"
            }
        }
    }
})
