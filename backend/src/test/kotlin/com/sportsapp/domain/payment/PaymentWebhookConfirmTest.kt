package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentWebhookConfirmTest : BehaviorSpec({

    fun setAuditFields(payment: Payment) {
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = payment.javaClass.superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(payment, ZonedDateTime.now())
        }
    }

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
    ).also {
        it.markReady(tid, "card", "http://checkout")
        setAuditFields(it)
    }

    Given("PAYMENT_APPROVED 웹훅 수신 — 도메인 이벤트 발행 확인") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_approve_events"
        val orderId = 777L
        val readyPayment = buildReadyPayment(
            tid = tid,
            idempotencyKey = "webhook-approve-events",
            orderType = OrderType.TICKETING,
            orderId = orderId,
        )
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().also { p -> setAuditFields(p) } }

        val capturedEvents = mutableListOf<DomainEvent>()
        every { domainEventPublisher.publishAll(any()) } answers {
            capturedEvents.addAll(firstArg<List<DomainEvent>>())
        }

        When("confirmWebhook(eventType=PAYMENT_APPROVED) 를 호출하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("주문 확정 이벤트가 단일 토픽으로 발행된다") {
                verify(exactly = 1) { domainEventPublisher.publishAll(any()) }
                val confirmed = capturedEvents.filterIsInstance<PaymentEvent.Confirmed>().single()
                confirmed.topic shouldBe "event.payment.payment.v1"
                confirmed.orderType shouldBe OrderType.TICKETING
                confirmed.orderId shouldBe orderId
                confirmed.paymentId shouldBe readyPayment.id
                confirmed.recipientUserId shouldBe readyPayment.userId
            }
        }
    }

    Given("이미 COMPLETED 인 Payment 에 PAYMENT_APPROVED 를 재수신 (멱등)") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_approve_idem"
        val completedPayment = buildReadyPayment(tid = tid, idempotencyKey = "webhook-approve-idem")
            .also { it.markCompleted(ZonedDateTime.now()) }
        every { paymentRepository.findByPgTransactionId(tid) } returns completedPayment

        When("이미 COMPLETED 상태에서 PAYMENT_APPROVED 웹훅을 수신하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("save 와 publishAll 이 호출되지 않는다 (멱등 early-return)") {
                verify(exactly = 0) { paymentRepository.save(any()) }
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
            }
        }
    }

    Given("PAYMENT_CANCELED 웹훅 수신 — 주문 취소 이벤트 발행 확인") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = domainEventPublisher,
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_cancel_events"
        val orderId = 300L
        val readyPayment = buildReadyPayment(
            tid = tid,
            idempotencyKey = "webhook-cancel-events",
            orderType = OrderType.BOOKING,
            orderId = orderId,
        )
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().also { p -> setAuditFields(p) } }

        val capturedEvents = mutableListOf<DomainEvent>()
        every { domainEventPublisher.publishAll(any()) } answers {
            capturedEvents.addAll(firstArg<List<DomainEvent>>())
        }

        When("confirmWebhook(eventType=PAYMENT_CANCELED) 를 호출하면") {
            service.confirmWebhook(tid = tid, eventType = "PAYMENT_CANCELED")

            Then("PaymentEvent.Cancelled 가 orderType/orderId/paymentId 와 함께 발행된다") {
                verify(exactly = 1) { domainEventPublisher.publishAll(any()) }
                val cancelled = capturedEvents.filterIsInstance<PaymentEvent.Cancelled>().single()
                cancelled.topic shouldBe "event.payment.payment.v1"
                cancelled.orderType shouldBe OrderType.BOOKING
                cancelled.orderId shouldBe orderId
                cancelled.paymentId shouldBe readyPayment.id
            }

            Then("확정 이벤트(PaymentEvent.Confirmed)는 발행되지 않는다") {
                capturedEvents.filterIsInstance<PaymentEvent.Confirmed>().size shouldBe 0
            }
        }
    }
})
