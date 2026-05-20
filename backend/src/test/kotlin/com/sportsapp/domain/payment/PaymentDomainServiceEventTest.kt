package com.sportsapp.domain.payment

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.payment.events.PaymentCompletedEvent
import com.sportsapp.domain.payment.events.PaymentFailedEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentDomainServiceEventTest : BehaviorSpec({

    Given("PG 성공 시") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = PaymentDomainService(paymentRepository, paymentGateway, domainEventPublisher)

        val key = "event-success-01"
        val approvedAt = ZonedDateTime.now()
        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentGateway.requestPayment(any()) } returns PaymentGatewayResult(
            pgTransactionId = "txn-ev-001",
            approvedAt = approvedAt,
        )

        When("create 를 호출하면") {
            service.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("30000"),
                currency = "KRW",
            )

            Then("[U-01] PaymentCompletedEvent 가 domainEventPublisher 로 발행된다") {
                val eventsSlot = slot<List<com.sportsapp.domain.common.DomainEvent>>()
                verify(exactly = 1) { domainEventPublisher.publishAll(capture(eventsSlot)) }
                val events = eventsSlot.captured
                events shouldHaveSize 1
                events[0].shouldBeInstanceOf<PaymentCompletedEvent>()
            }
        }
    }

    Given("PG 실패 시") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = PaymentDomainService(paymentRepository, paymentGateway, domainEventPublisher)

        val key = "event-fail-01"
        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentGateway.requestPayment(any()) } throws PaymentGatewayException("카드 한도 초과")

        When("create 를 호출하면") {
            service.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 11L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("30000"),
                currency = "KRW",
            )

            Then("[U-02] PaymentFailedEvent 가 reason 포함하여 domainEventPublisher 로 발행된다") {
                val eventsSlot = slot<List<com.sportsapp.domain.common.DomainEvent>>()
                verify(exactly = 1) { domainEventPublisher.publishAll(capture(eventsSlot)) }
                val events = eventsSlot.captured
                events shouldHaveSize 1
                val failedEvent = events[0].shouldBeInstanceOf<PaymentFailedEvent>()
                failedEvent.reason shouldBe "카드 한도 초과"
            }
        }
    }

    Given("Payment.markCompleted 호출 시") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "pull-test-01",
            orderType = OrderType.BOOKING,
            orderId = 20L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )

        When("markCompleted 후 pullDomainEvents 를 호출하면") {
            payment.markCompleted(ZonedDateTime.now())
            val events = payment.pullDomainEvents()

            Then("[U-01] PaymentCompletedEvent 가 1건 적재된다") {
                events shouldHaveSize 1
                events[0].shouldBeInstanceOf<PaymentCompletedEvent>()
            }

            Then("[U-03] pullDomainEvents 재호출 시 내부 리스트가 비워진다") {
                payment.pullDomainEvents() shouldHaveSize 0
            }
        }
    }

    Given("Payment.markFailed 호출 시") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "pull-test-02",
            orderType = OrderType.BOOKING,
            orderId = 21L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )

        When("markFailed 후 pullDomainEvents 를 호출하면") {
            payment.markFailed("PG 오류")
            val events = payment.pullDomainEvents()

            Then("[U-02] PaymentFailedEvent 가 reason 포함하여 1건 적재된다") {
                events shouldHaveSize 1
                val failedEvent = events[0].shouldBeInstanceOf<PaymentFailedEvent>()
                failedEvent.reason shouldBe "PG 오류"
            }
        }
    }
})
