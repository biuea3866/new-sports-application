package com.sportsapp.domain.payment
import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.gateway.PgPrepareResult
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod

import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentDomainServiceTest : BehaviorSpec({

    fun setAuditFields(payment: Payment) {
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = payment.javaClass.superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(payment, ZonedDateTime.now())
        }
    }

    fun buildPrepareRequest() = Triple(
        mockk<PaymentRepository>(),
        mockk<PaymentGateway>(),
        "test-key-01"
    )

    Given("prepare — PG 성공 케이스") {
        val (paymentRepository, paymentGateway, key) = buildPrepareRequest()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        )

        every { paymentRepository.findByIdempotencyKey(key) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }
        every { paymentGateway.prepare(any()) } returns PgPrepareResult(
            tid = "MOCK_CARD_abc123",
            provider = "card",
            checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc123",
        )

        When("prepare 를 호출하면") {
            val result = service.prepare(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
                itemName = "테스트 예약",
                returnUrl = "http://localhost/return",
                failUrl = "http://localhost/fail",
            )
            Then("READY 상태의 Payment 가 반환되고 checkoutUrl 이 설정된다") {
                result.status shouldBe PaymentStatus.READY
                result.pgTransactionId shouldBe "MOCK_CARD_abc123"
                result.checkoutUrl shouldBe "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc123"
                verify(exactly = 1) { paymentGateway.prepare(any()) }
                verify(exactly = 1) { paymentRepository.save(any()) }
            }
        }
    }

    Given("prepare — 멱등 hit 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        )

        val key = "idem-hit-key"
        val existing = Payment.create(
            userId = 1L,
            idempotencyKey = key,
            orderType = OrderType.BOOKING,
            orderId = 200L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also {
            it.markReady("tid-existing", "card", "http://localhost:9090/pg/card/checkout?tid=tid-existing")
            it.markCompleted(ZonedDateTime.now())
        }
        every { paymentRepository.findByIdempotencyKey(key) } returns existing

        When("prepare 를 호출하면") {
            val result = service.prepare(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
                itemName = "테스트",
                returnUrl = "",
                failUrl = "",
            )
            Then("PG 호출 없이 기존 Payment 를 반환한다") {
                result shouldBe existing
                verify(exactly = 0) { paymentGateway.prepare(any()) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }
    }

    Given("confirmWebhook — PAYMENT_APPROVED 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_approve01"
        val readyPayment = Payment.create(
            userId = 1L,
            idempotencyKey = "confirm-key-01",
            orderType = OrderType.BOOKING,
            orderId = 300L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("15000"),
            currency = "KRW",
        ).also {
            it.markReady(tid, "card", "http://checkout")
            setAuditFields(it)
        }
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().also { p -> setAuditFields(p) } }

        When("confirmWebhook(eventType=PAYMENT_APPROVED) 를 호출하면") {
            val result = service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("상태가 COMPLETED 로 전이된다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 1) { paymentRepository.save(any()) }
            }
        }
    }

    Given("confirmWebhook — PAYMENT_CANCELED 케이스") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_cancel01"
        val readyPayment = Payment.create(
            userId = 1L,
            idempotencyKey = "confirm-key-02",
            orderType = OrderType.BOOKING,
            orderId = 400L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("20000"),
            currency = "KRW",
        ).also {
            it.markReady(tid, "card", "http://checkout")
            setAuditFields(it)
        }
        every { paymentRepository.findByPgTransactionId(tid) } returns readyPayment
        every { paymentRepository.save(any()) } answers { firstArg<Payment>().also { p -> setAuditFields(p) } }

        When("confirmWebhook(eventType=PAYMENT_CANCELED) 를 호출하면") {
            val result = service.confirmWebhook(tid = tid, eventType = "PAYMENT_CANCELED")

            Then("상태가 CANCELLED 로 전이된다") {
                result.status shouldBe PaymentStatus.CANCELLED
            }
        }
    }

    Given("confirmWebhook — 멱등 케이스 (PAYMENT_APPROVED 중복 수신)") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = PaymentDomainService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            domainEventPublisher = mockk(relaxed = true),
            transactionTemplate = mockk(relaxed = true),
        )

        val tid = "MOCK_CARD_dup01"
        val completedPayment = Payment.create(
            userId = 1L,
            idempotencyKey = "confirm-key-03",
            orderType = OrderType.BOOKING,
            orderId = 500L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("25000"),
            currency = "KRW",
        ).also {
            it.markReady(tid, "card", "http://checkout")
            it.markCompleted(ZonedDateTime.now())
            setAuditFields(it)
        }
        every { paymentRepository.findByPgTransactionId(tid) } returns completedPayment

        When("이미 COMPLETED 상태에서 PAYMENT_APPROVED webhook 을 다시 수신하면") {
            val result = service.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")

            Then("save 를 호출하지 않고 기존 상태를 반환한다 (멱등)") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }
    }
})
