package com.sportsapp.domain.payment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentDomainServiceTest : BehaviorSpec({

    Given("create — PG 성공 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(paymentRepository, paymentGateway)

        val key = "create-success-01"
        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentGateway.requestPayment(any()) } returns PaymentGatewayResult(
            pgTransactionId = "txn-001",
            approvedAt = ZonedDateTime.now(),
        )

        When("create 를 호출하면") {
            val result = service.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            Then("[U-02] COMPLETED 상태의 Payment 가 반환된다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 1) { paymentGateway.requestPayment(any()) }
                verify(exactly = 1) { paymentRepository.save(any()) }
            }
        }
    }

    Given("create — PG 실패 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(paymentRepository, paymentGateway)

        val key = "create-fail-01"
        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentGateway.requestPayment(any()) } throws PaymentGatewayException("카드 한도 초과")

        When("create 를 호출하면") {
            val result = service.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 101L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            Then("[U-02] FAILED 상태와 failureReason 이 담긴 Payment 가 반환된다") {
                result.status shouldBe PaymentStatus.FAILED
                result.failureReason shouldBe "카드 한도 초과"
                verify(exactly = 1) { paymentRepository.save(any()) }
            }
        }
    }

    Given("create — 멱등 hit 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(paymentRepository, paymentGateway)

        val key = "create-idem-hit-01"
        val existing = Payment.create(
            userId = 1L,
            idempotencyKey = key,
            orderType = OrderType.BOOKING,
            orderId = 200L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also { it.markCompleted(ZonedDateTime.now()) }
        every { paymentRepository.findByIdempotencyKey(key) } returns existing

        When("create 를 호출하면") {
            val result = service.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            Then("[U-02] PG 호출 없이 기존 Payment 를 반환한다") {
                result shouldBe existing
                verify(exactly = 0) { paymentGateway.requestPayment(any()) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }
    }
})
