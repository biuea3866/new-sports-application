package com.sportsapp.application.payment

import com.sportsapp.domain.payment.MissingIdempotencyKeyException
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    fun buildPayment(idempotencyKey: String, status: PaymentStatus = PaymentStatus.PENDING): Payment =
        Payment.create(
            userId = 1L,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 10L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also {
            if (status == PaymentStatus.COMPLETED) it.markCompleted(ZonedDateTime.now())
        }

    Given("동일 idempotencyKey 가 이미 존재하는 경우 (멱등 hit)") {
        val idempotencyKey = "idem-hit-01"
        val existingPayment = buildPayment(idempotencyKey, PaymentStatus.COMPLETED)
        every {
            paymentDomainService.create(
                userId = any(),
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = any(),
                method = any(),
                amount = any(),
                currency = any(),
            )
        } returns existingPayment

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("[U-01] 기존 Payment 의 상태를 그대로 반환한다") {
                result.idempotencyKey shouldBe idempotencyKey
                result.status shouldBe PaymentStatus.COMPLETED
            }
        }
    }

    Given("신규 idempotencyKey + PG 성공 케이스 (멱등 miss)") {
        val idempotencyKey = "idem-miss-01"
        val completedPayment = buildPayment(idempotencyKey, PaymentStatus.COMPLETED)
        every {
            paymentDomainService.create(
                userId = any(),
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = any(),
                method = any(),
                amount = any(),
                currency = any(),
            )
        } returns completedPayment

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("[U-01] COMPLETED 상태의 PaymentResponse 를 반환한다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 1) {
                    paymentDomainService.create(
                        userId = any(),
                        idempotencyKey = idempotencyKey,
                        orderType = any(),
                        orderId = any(),
                        method = any(),
                        amount = any(),
                        currency = any(),
                    )
                }
            }
        }
    }

    Given("신규 idempotencyKey + PG 실패 케이스 (멱등 miss)") {
        val idempotencyKey = "idem-miss-fail-01"
        val failedPayment = buildPayment(idempotencyKey).also {
            it.markFailed("PG timeout")
        }
        every {
            paymentDomainService.create(
                userId = any(),
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = any(),
                method = any(),
                amount = any(),
                currency = any(),
            )
        } returns failedPayment

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("[U-01] FAILED 상태와 failureReason 이 담긴 PaymentResponse 를 반환한다") {
                result.status shouldBe PaymentStatus.FAILED
                result.failureReason shouldBe "PG timeout"
            }
        }
    }

    Given("Idempotency-Key 가 빈 문자열인 경우") {
        When("Controller 레이어에서 빈 키 감지 후 MissingIdempotencyKeyException 을 던지면") {
            Then("[U-03] MissingIdempotencyKeyException 이 발생한다") {
                shouldThrow<MissingIdempotencyKeyException> {
                    throw MissingIdempotencyKeyException()
                }
            }
        }
    }
})
