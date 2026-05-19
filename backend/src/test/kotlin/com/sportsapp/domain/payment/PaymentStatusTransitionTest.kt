package com.sportsapp.domain.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentStatusTransitionTest : BehaviorSpec({

    Given("PaymentStatus 상태 전이 표") {
        When("PENDING → COMPLETED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.PENDING.canTransitTo(PaymentStatus.COMPLETED) shouldBe true
            }
        }
        When("PENDING → FAILED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.PENDING.canTransitTo(PaymentStatus.FAILED) shouldBe true
            }
        }
        When("COMPLETED → REFUNDED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.COMPLETED.canTransitTo(PaymentStatus.REFUNDED) shouldBe true
            }
        }
        When("COMPLETED → PENDING") {
            Then("[U-04] canTransitTo 는 false 를 반환한다") {
                PaymentStatus.COMPLETED.canTransitTo(PaymentStatus.PENDING) shouldBe false
            }
        }
        When("FAILED → PENDING") {
            Then("[U-04] canTransitTo 는 false 를 반환한다") {
                PaymentStatus.FAILED.canTransitTo(PaymentStatus.PENDING) shouldBe false
            }
        }
        When("REFUNDED → COMPLETED") {
            Then("[U-04] canTransitTo 는 false 를 반환한다") {
                PaymentStatus.REFUNDED.canTransitTo(PaymentStatus.COMPLETED) shouldBe false
            }
        }
    }

    Given("PENDING 상태의 Payment") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-001",
            orderType = OrderType.BOOKING,
            orderId = 10L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )

        When("markCompleted 를 호출하면") {
            val paidAt = ZonedDateTime.now()
            payment.markCompleted(paidAt)
            Then("[U-01] status 가 COMPLETED 로 바뀌고 paidAt 이 채워진다") {
                payment.status shouldBe PaymentStatus.COMPLETED
                payment.paidAt shouldBe paidAt
            }
        }
    }

    Given("COMPLETED 상태의 Payment") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-002",
            orderType = OrderType.TICKETING,
            orderId = 20L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("20000"),
            currency = "KRW",
        ).also { it.markCompleted(ZonedDateTime.now()) }

        When("markCompleted 를 다시 호출하면") {
            Then("[U-02] InvalidPaymentStateException 을 던진다") {
                shouldThrow<InvalidPaymentStateException> {
                    payment.markCompleted(ZonedDateTime.now())
                }
            }
        }
    }

    Given("PENDING 상태의 Payment 에 빈 reason 으로 markFailed 호출") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-003",
            orderType = OrderType.GOODS,
            orderId = 30L,
            method = PaymentMethod.BANK_TRANSFER,
            amount = BigDecimal("5000"),
            currency = "KRW",
        )

        When("reason 이 빈 문자열이면") {
            Then("[U-03] InvalidFailureReasonException 을 던진다") {
                shouldThrow<InvalidFailureReasonException> {
                    payment.markFailed("")
                }
            }
        }
    }
})
