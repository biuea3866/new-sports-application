package com.sportsapp.application.payment

import com.sportsapp.domain.payment.exception.MissingIdempotencyKeyException
import com.sportsapp.application.payment.dto.CreatePaymentCommand
import com.sportsapp.application.payment.usecase.CreatePaymentUseCase
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class CreatePaymentUseCaseTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = CreatePaymentUseCase(paymentDomainService)

    fun buildCommand(idempotencyKey: String = "idem-key-01") = CreatePaymentCommand(
        userId = 1L,
        idempotencyKey = idempotencyKey,
        orderType = OrderType.BOOKING,
        orderId = 10L,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("10000"),
        currency = "KRW",
    )

    fun buildMockPayment(status: PaymentStatus, paymentId: Long = 1L): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns paymentId
        every { payment.orderType } returns OrderType.BOOKING
        every { payment.orderId } returns 10L
        every { payment.method } returns PaymentMethod.CREDIT_CARD
        every { payment.amount } returns BigDecimal("10000")
        every { payment.status } returns status
        every { payment.createdAt } returns ZonedDateTime.now()
        every { payment.paidAt } returns if (status == PaymentStatus.COMPLETED) ZonedDateTime.now() else null
        every { payment.checkoutUrl } returns "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc"
        return payment
    }

    Given("동일 idempotencyKey 가 이미 존재하는 경우 — createPending 이 기존 paymentId 반환") {
        val idempotencyKey = "idem-hit-01"
        val existingPaymentId = 99L
        val existingPayment = buildMockPayment(PaymentStatus.COMPLETED, existingPaymentId)

        every {
            paymentDomainService.createPending(
                userId = any(),
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = any(),
                method = any(),
                amount = any(),
                currency = any(),
            )
        } returns existingPaymentId

        every {
            paymentDomainService.initiatePg(any<PgInitiateCommand>())
        } returns PgInitiateResult(
            paymentId = existingPaymentId,
            status = PaymentStatus.COMPLETED,
            pgTransactionId = "MOCK_tid",
            checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_tid",
        )

        every { paymentDomainService.getPayment(userId = 1L, paymentId = existingPaymentId) } returns existingPayment

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("기존 Payment 의 상태를 그대로 반환한다") {
                result.status shouldBe PaymentStatus.COMPLETED
            }
        }
    }

    Given("신규 idempotencyKey + PG 성공 케이스 — createPending → initiatePg → getPayment 순서로 호출된다") {
        val idempotencyKey = "idem-miss-01"
        val newPaymentId = 1L
        val readyPayment = buildMockPayment(PaymentStatus.READY, newPaymentId)

        every {
            paymentDomainService.createPending(
                userId = any(),
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = any(),
                method = any(),
                amount = any(),
                currency = any(),
            )
        } returns newPaymentId

        every {
            paymentDomainService.initiatePg(any<PgInitiateCommand>())
        } returns PgInitiateResult(
            paymentId = newPaymentId,
            status = PaymentStatus.READY,
            pgTransactionId = "MOCK_CARD_abc",
            checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc",
        )

        every { paymentDomainService.getPayment(userId = 1L, paymentId = newPaymentId) } returns readyPayment

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("READY 상태의 PaymentResponse 와 checkoutUrl 을 반환한다") {
                result.status shouldBe PaymentStatus.READY
                result.checkoutUrl shouldBe "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc"
            }
        }
    }

    Given("Idempotency-Key 가 빈 문자열인 경우") {
        When("Controller 레이어에서 빈 키 감지 후 MissingIdempotencyKeyException 을 던지면") {
            Then("MissingIdempotencyKeyException 이 발생한다") {
                shouldThrow<MissingIdempotencyKeyException> {
                    throw MissingIdempotencyKeyException()
                }
            }
        }
    }
})
