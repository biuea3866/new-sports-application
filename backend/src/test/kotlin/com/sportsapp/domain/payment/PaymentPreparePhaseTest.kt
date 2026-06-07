package com.sportsapp.domain.payment
import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.event.PaymentCompletedEvent
import com.sportsapp.domain.payment.gateway.OrderConfirmationGateway
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.exception.PaymentGatewayException
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
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal

class PaymentPreparePhaseTest : BehaviorSpec({

    fun buildService(
        paymentRepository: PaymentRepository = mockk(),
        paymentGateway: PaymentGateway = mockk(),
        transactionTemplate: TransactionTemplate = mockk(),
    ) = PaymentDomainService(
        paymentRepository = paymentRepository,
        paymentGateway = paymentGateway,
        orderConfirmationGateway = mockk(relaxed = true),
        domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true),
        transactionTemplate = transactionTemplate,
    )

    fun buildInitiateCommand(paymentId: Long, idempotencyKey: String) = PgInitiateCommand(
        paymentId = paymentId,
        method = PaymentMethod.CREDIT_CARD,
        idempotencyKey = idempotencyKey,
        userId = 1L,
        orderType = OrderType.BOOKING,
        orderId = 300L,
        amount = BigDecimal("10000"),
        currency = "KRW",
        itemName = "테스트 예약",
        returnUrl = "http://return",
        failUrl = "http://fail",
    )

    Given("신규 idempotencyKey 로 createPending 을 호출할 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = buildService(paymentRepository = paymentRepository, paymentGateway = paymentGateway)
        val idempotencyKey = "pending-new-01"

        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns null
        every { paymentRepository.save(any()) } answers { firstArg() }

        When("createPending 을 호출하면") {
            service.createPending(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )

            Then("PENDING 상태의 Payment 가 저장되고 PG 호출은 발생하지 않는다") {
                verify(exactly = 1) { paymentRepository.save(any()) }
                verify(exactly = 0) { paymentGateway.prepare(any()) }
            }
        }
    }

    Given("기존 idempotencyKey 로 createPending 을 호출할 때 (멱등)") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val service = buildService(paymentRepository = paymentRepository, paymentGateway = paymentGateway)
        val idempotencyKey = "pending-idem-01"

        val existingPayment = Payment.create(
            userId = 1L,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 200L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )
        every { paymentRepository.findByIdempotencyKey(idempotencyKey) } returns existingPayment

        When("createPending 을 호출하면") {
            val paymentId = service.createPending(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )

            Then("PG 호출 없이 기존 Payment 의 id 가 반환된다") {
                paymentId shouldBe existingPayment.id
                verify(exactly = 0) { paymentGateway.prepare(any()) }
                verify(exactly = 0) { paymentRepository.save(any()) }
            }
        }
    }

    Given("initiatePg 를 호출할 때 PG 가 성공 응답을 반환하면") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val transactionTemplate = mockk<TransactionTemplate>()
        val service = buildService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            transactionTemplate = transactionTemplate,
        )

        val pendingPayment = Payment.create(
            userId = 1L,
            idempotencyKey = "initiate-ok-01",
            orderType = OrderType.BOOKING,
            orderId = 300L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )
        val paymentId = pendingPayment.id
        val tid = "MOCK_CARD_ok01"
        val checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_ok01"

        every { paymentGateway.prepare(any()) } returns PgPrepareResult(
            tid = tid,
            provider = "card",
            checkoutUrl = checkoutUrl,
        )
        every { transactionTemplate.execute<PgInitiateResult>(any()) } answers {
            val callback = firstArg<TransactionCallback<PgInitiateResult>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
        every { paymentRepository.findById(paymentId) } returns pendingPayment
        every { paymentRepository.save(any()) } answers { firstArg() }

        When("initiatePg 를 호출하면") {
            val result = service.initiatePg(buildInitiateCommand(paymentId, "initiate-ok-01"))

            Then("Payment 의 status 가 READY 로 전이되고 pgTransactionId 와 checkoutUrl 이 설정된다") {
                verify(exactly = 1) { paymentGateway.prepare(any()) }
                result.status shouldBe PaymentStatus.READY
                result.pgTransactionId shouldBe tid
                result.checkoutUrl shouldBe checkoutUrl
            }
        }
    }

    Given("initiatePg 를 호출할 때 PaymentGatewayException 이 발생하면") {
        val paymentRepository = mockk<PaymentRepository>()
        val paymentGateway = mockk<PaymentGateway>()
        val transactionTemplate = mockk<TransactionTemplate>()
        val service = buildService(
            paymentRepository = paymentRepository,
            paymentGateway = paymentGateway,
            transactionTemplate = transactionTemplate,
        )

        val pendingPayment = Payment.create(
            userId = 1L,
            idempotencyKey = "initiate-fail-01",
            orderType = OrderType.BOOKING,
            orderId = 400L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )
        val paymentId = pendingPayment.id

        every { paymentGateway.prepare(any()) } throws PaymentGatewayException("PG timeout")
        every { transactionTemplate.execute<PgInitiateResult>(any()) } answers {
            val callback = firstArg<TransactionCallback<PgInitiateResult>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
        every { paymentRepository.findById(paymentId) } returns pendingPayment
        every { paymentRepository.save(any()) } answers { firstArg() }

        When("initiatePg 를 호출하면") {
            val result = service.initiatePg(buildInitiateCommand(paymentId, "initiate-fail-01"))

            Then("Payment 의 status 가 FAILED 로 전이되고 예외가 전파되지 않는다") {
                verify(exactly = 1) { paymentGateway.prepare(any()) }
                verify(exactly = 1) { paymentRepository.save(any()) }
                result.status shouldBe PaymentStatus.FAILED
            }
        }
    }
})
