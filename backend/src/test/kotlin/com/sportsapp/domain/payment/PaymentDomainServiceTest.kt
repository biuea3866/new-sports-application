package com.sportsapp.domain.payment

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class PaymentDomainServiceTest : BehaviorSpec({

    val paymentRepository = mockk<PaymentRepository>()
    val paymentGateway = mockk<PaymentGateway>()
    val service = PaymentDomainService(paymentRepository, paymentGateway)

    Given("idempotencyKey 가 이미 존재하는 경우 (S-01 멱등)") {
        val key = "existing-key"
        val existing = Payment.create(
            userId = 1L,
            idempotencyKey = key,
            orderType = OrderType.BOOKING,
            orderId = 10L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )
        every { paymentRepository.findByIdempotencyKey(key) } returns existing

        When("initiatePayment 를 호출하면") {
            val result = service.initiatePayment(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            Then("[S-01] PG 호출 없이 기존 Payment 를 반환한다") {
                result shouldBe existing
                verify(exactly = 0) { paymentGateway.requestPayment(any()) }
            }
        }
    }

    Given("신규 idempotencyKey 인 경우") {
        val key = "new-key"
        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }

        When("initiatePayment 를 호출하면") {
            val result = service.initiatePayment(
                userId = 2L,
                idempotencyKey = key,
                orderType = OrderType.TICKETING,
                orderId = 20L,
                method = PaymentMethod.BANK_TRANSFER,
                amount = BigDecimal("5000"),
                currency = "KRW",
            )
            Then("PENDING 상태의 Payment 가 저장되어 반환된다") {
                result.status shouldBe PaymentStatus.PENDING
                result.idempotencyKey shouldBe key
                verify(exactly = 1) { paymentRepository.save(any()) }
            }
        }
    }
})
