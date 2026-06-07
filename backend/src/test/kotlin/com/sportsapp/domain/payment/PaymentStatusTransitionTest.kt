package com.sportsapp.domain.payment

import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.exception.InvalidFailureReasonException
import com.sportsapp.domain.payment.exception.InvalidPaymentStateException
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.vo.toPgProviderName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentStatusTransitionTest : BehaviorSpec({

    Given("PaymentStatus 상태 전이 표") {
        When("PENDING → READY") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.PENDING.canTransitTo(PaymentStatus.READY) shouldBe true
            }
        }
        When("PENDING → COMPLETED") {
            Then("[P1] canTransitTo 는 false 를 반환한다 — 새 플로우는 PENDING→READY→COMPLETED 강제") {
                PaymentStatus.PENDING.canTransitTo(PaymentStatus.COMPLETED) shouldBe false
            }
        }
        When("PENDING → FAILED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.PENDING.canTransitTo(PaymentStatus.FAILED) shouldBe true
            }
        }
        When("READY → COMPLETED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.READY.canTransitTo(PaymentStatus.COMPLETED) shouldBe true
            }
        }
        When("READY → CANCELLED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.READY.canTransitTo(PaymentStatus.CANCELLED) shouldBe true
            }
        }
        When("READY → FAILED") {
            Then("[U-04] canTransitTo 는 true 를 반환한다") {
                PaymentStatus.READY.canTransitTo(PaymentStatus.FAILED) shouldBe true
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
        When("CANCELLED → COMPLETED") {
            Then("[U-04] canTransitTo 는 false 를 반환한다") {
                PaymentStatus.CANCELLED.canTransitTo(PaymentStatus.COMPLETED) shouldBe false
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

        When("markReady 를 호출하면") {
            payment.markReady("MOCK_CARD_tid001", "card", "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_tid001")
            Then("[U-01] status 가 READY 로 바뀌고 pgTransactionId / checkoutUrl 이 채워진다") {
                payment.status shouldBe PaymentStatus.READY
                payment.pgTransactionId shouldBe "MOCK_CARD_tid001"
                payment.checkoutUrl shouldBe "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_tid001"
            }
        }
    }

    Given("READY 상태의 Payment") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-002",
            orderType = OrderType.TICKETING,
            orderId = 20L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("20000"),
            currency = "KRW",
        ).also { it.markReady("tid-002", "card", "http://checkout") }

        When("markCompleted 를 호출하면") {
            val paidAt = ZonedDateTime.now()
            payment.markCompleted(paidAt)
            Then("[U-05] status 가 COMPLETED 로 바뀌고 paidAt 이 채워진다") {
                payment.status shouldBe PaymentStatus.COMPLETED
                payment.paidAt shouldBe paidAt
            }
        }
    }

    Given("READY 상태의 Payment 에 markCancelled 호출") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-003",
            orderType = OrderType.GOODS,
            orderId = 30L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("5000"),
            currency = "KRW",
        ).also { it.markReady("tid-003", "card", "http://checkout") }

        When("markCancelled 를 호출하면") {
            payment.markCancelled()
            Then("[U-06] status 가 CANCELLED 로 바뀐다") {
                payment.status shouldBe PaymentStatus.CANCELLED
            }
        }
    }

    Given("COMPLETED 상태의 Payment") {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = "key-004",
            orderType = OrderType.TICKETING,
            orderId = 40L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("20000"),
            currency = "KRW",
        ).also {
            it.markReady("tid-004", "card", "http://checkout")
            it.markCompleted(ZonedDateTime.now())
        }

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
            idempotencyKey = "key-005",
            orderType = OrderType.GOODS,
            orderId = 50L,
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

    Given("PaymentMethod.toPgProviderName 변환 검증") {
        When("CREDIT_CARD 를 변환하면") {
            Then("[U-07] 'card' 를 반환한다") {
                PaymentMethod.CREDIT_CARD.toPgProviderName() shouldBe "card"
            }
        }
        When("BANK_TRANSFER 를 변환하면") {
            Then("[U-07] 'bank_transfer' 를 반환한다") {
                PaymentMethod.BANK_TRANSFER.toPgProviderName() shouldBe "bank_transfer"
            }
        }
        When("MOBILE_PAY 를 변환하면") {
            Then("[U-07] DEF-005 — 500 에러 없이 'card' 를 반환한다") {
                PaymentMethod.MOBILE_PAY.toPgProviderName() shouldBe "card"
            }
        }
        When("KAKAO 를 변환하면") {
            Then("[U-07] 'kakao' 를 반환한다") {
                PaymentMethod.KAKAO.toPgProviderName() shouldBe "kakao"
            }
        }
    }
})
