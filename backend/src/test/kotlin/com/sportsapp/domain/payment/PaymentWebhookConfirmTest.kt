package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentWebhookConfirmTest : BehaviorSpec({

    fun buildReadyPayment(
        tid: String,
        idempotencyKey: String,
        orderType: OrderType = OrderType.BOOKING,
        orderId: Long = 300L,
    ): Payment = Payment.create(
        userId = 1L,
        idempotencyKey = idempotencyKey,
        orderType = orderType,
        orderId = orderId,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("15000"),
        currency = "KRW",
    ).also { it.markReady(tid, "card", "http://checkout") }

    Given("PAYMENT_APPROVED 웹훅 수신 — 도메인 이벤트 발행 확인") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val orderConfirmationGateway = mockk<OrderConfirmationGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            orderConfirmationGateway = orderConfirmationGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_u04_01"
        val readyPayment = buildReadyPayment(tid = tid, idempotencyKey = "webhook-u04-key")
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg() }
        justRun { orderConfirmationGateway.confirm(any(), any(), any()) }

        val capturedEvents = mutableListOf<DomainEvent>()
        every { domainEventPublisher.publishAll(any()) } answers {
            capturedEvents.addAll(firstArg<List<DomainEvent>>())
        }

        When("confirmWebhook(eventType=PAYMENT_APPROVED) 를 호출하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("pullDomainEvents() 가 PaymentCompletedEvent 1건을 publishAll 로 전달한다") {
                verify(exactly = 1) { domainEventPublisher.publishAll(any()) }
                capturedEvents.size shouldBe 1
                val event = capturedEvents[0]
                (event is PaymentCompletedEvent) shouldBe true
                event.topic shouldBe "payment.completed.v1"
            }
        }
    }

    Given("이미 COMPLETED 인 Payment 에 PAYMENT_APPROVED 를 재수신 (멱등)") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val orderConfirmationGateway = mockk<OrderConfirmationGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            orderConfirmationGateway = orderConfirmationGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_u05_01"
        val completedPayment = buildReadyPayment(tid = tid, idempotencyKey = "webhook-u05-key")
            .also { it.markCompleted(ZonedDateTime.now()) }
        every { paymentRepository.findByPgTransactionId(tid) } returns completedPayment

        When("이미 COMPLETED 상태에서 PAYMENT_APPROVED 웹훅을 수신하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("save 와 publishAll 이 호출되지 않는다 (멱등 early-return)") {
                verify(exactly = 0) { paymentRepository.save(any()) }
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
                verify(exactly = 0) { orderConfirmationGateway.confirm(any(), any(), any()) }
            }
        }
    }

    Given("PAYMENT_APPROVED 처리 시 OrderConfirmationGateway 호출 검증") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val orderConfirmationGateway = mockk<OrderConfirmationGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            orderConfirmationGateway = orderConfirmationGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_u06_01"
        val orderId = 777L
        val readyPayment = buildReadyPayment(
            tid = tid,
            idempotencyKey = "webhook-u06-key",
            orderType = OrderType.TICKETING,
            orderId = orderId,
        )
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg() }
        justRun { orderConfirmationGateway.confirm(any(), any(), any()) }
        every { domainEventPublisher.publishAll(any()) } returns Unit

        When("confirmWebhook(eventType=PAYMENT_APPROVED) 를 호출하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("OrderConfirmationGateway.confirm 이 payment.orderType 과 payment.orderId 로 1회 호출된다") {
                verify(exactly = 1) {
                    orderConfirmationGateway.confirm(
                        orderType = OrderType.TICKETING,
                        orderId = orderId,
                        paymentId = readyPayment.id,
                    )
                }
            }
        }
    }

    Given("PAYMENT_CANCELED 처리 시 도메인 이벤트 미발행 확인") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val orderConfirmationGateway = mockk<OrderConfirmationGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            orderConfirmationGateway = orderConfirmationGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_u07_01"
        val readyPayment = buildReadyPayment(tid = tid, idempotencyKey = "webhook-u07-key")
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg() }

        When("confirmWebhook(eventType=PAYMENT_CANCELED) 를 호출하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_CANCELED")

            Then("PaymentCompletedEvent 가 발행되지 않는다 (publishAll 미호출)") {
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
                verify(exactly = 0) { orderConfirmationGateway.confirm(any(), any(), any()) }
            }
        }
    }
})
