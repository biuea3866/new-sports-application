package com.sportsapp.domain.payment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentCompletedEventTest : BehaviorSpec({

    Given("READY 상태 Payment 에서 markCompleted 를 호출하면") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "event-test-key-01",
            orderType = OrderType.BOOKING,
            orderId = 100L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also { it.markReady("tid-event-01", "card", "http://checkout") }

        When("markCompleted 를 호출하면") {
            val paidAt = ZonedDateTime.now()
            payment.markCompleted(paidAt)

            Then("status 가 COMPLETED 로 전이되고 PaymentCompletedEvent 가 1건 적재된다") {
                payment.status shouldBe PaymentStatus.COMPLETED
                val events = payment.pullDomainEvents()
                events.size shouldBe 1
                val event = events[0] as PaymentCompletedEvent
                event.aggregateId shouldBe payment.id
                event.topic shouldBe "payment.completed.v1"
            }
        }
    }

    Given("PENDING 상태 Payment 에서 markCompleted 를 호출하면") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "event-test-key-02",
            orderType = OrderType.BOOKING,
            orderId = 101L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )

        When("markCompleted 를 호출하면") {
            Then("InvalidPaymentStateException 이 던져진다") {
                io.kotest.assertions.throwables.shouldThrow<InvalidPaymentStateException> {
                    payment.markCompleted(ZonedDateTime.now())
                }
            }
        }
    }

    Given("PaymentCompletedEvent 인스턴스") {
        val paymentId = 42L
        val event = PaymentCompletedEvent(paymentId = paymentId)

        When("topic 과 aggregateId 를 확인하면") {
            Then("topic 은 payment.completed.v1 이고 aggregateId 는 paymentId 와 일치한다") {
                event.topic shouldBe "payment.completed.v1"
                event.aggregateId shouldBe paymentId
            }
        }
    }
})
