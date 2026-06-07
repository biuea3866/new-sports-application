package com.sportsapp.application.payment
import com.sportsapp.application.payment.dto.CreatePaymentCommand
import com.sportsapp.application.payment.usecase.CreatePaymentUseCase

import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class CreatePaymentWithPgMethodTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = CreatePaymentUseCase(paymentDomainService)

    fun buildCommand(method: PaymentMethod) = CreatePaymentCommand(
        userId = 1L,
        idempotencyKey = "idem-${method.name}-01",
        orderType = OrderType.BOOKING,
        orderId = 10L,
        method = method,
        amount = BigDecimal("15000"),
        currency = "KRW",
    )

    fun buildMockPayment(method: PaymentMethod, status: PaymentStatus): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns 1L
        every { payment.orderType } returns OrderType.BOOKING
        every { payment.orderId } returns 10L
        every { payment.method } returns method
        every { payment.amount } returns BigDecimal("15000")
        every { payment.status } returns status
        every { payment.createdAt } returns ZonedDateTime.now()
        every { payment.paidAt } returns if (status == PaymentStatus.COMPLETED) ZonedDateTime.now() else null
        every { payment.checkoutUrl } returns "http://localhost:9090/pg/card/checkout?tid=MOCK_tid"
        return payment
    }

    listOf(
        PaymentMethod.KAKAO,
        PaymentMethod.TOSS,
        PaymentMethod.NAVER,
        PaymentMethod.DANAL,
        PaymentMethod.BANK_TRANSFER,
        PaymentMethod.CREDIT_CARD,
    ).forEach { method ->
        Given("method=$method 로 결제 요청 — PG 성공") {
            val readyPayment = buildMockPayment(method, PaymentStatus.READY)
            every {
                paymentDomainService.createPending(
                    userId = any(),
                    idempotencyKey = any(),
                    orderType = any(),
                    orderId = any(),
                    method = method,
                    amount = any(),
                    currency = any(),
                )
            } returns 1L

            every {
                paymentDomainService.initiatePg(any<PgInitiateCommand>())
            } returns PgInitiateResult(
                paymentId = 1L,
                status = PaymentStatus.READY,
                pgTransactionId = "MOCK_tid",
                checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_tid",
            )

            every { paymentDomainService.getPayment(userId = 1L, paymentId = 1L) } returns readyPayment

            When("execute 를 호출하면") {
                val result = useCase.execute(buildCommand(method))

                Then("method=$method 로 READY 상태의 PaymentResponse 를 반환한다") {
                    result.status shouldBe PaymentStatus.READY
                    result.method shouldBe method
                }
            }
        }

        Given("method=$method 로 결제 요청 — PG 실패(FAILED 상태)") {
            val failedPayment = buildMockPayment(method, PaymentStatus.FAILED)
            every {
                paymentDomainService.createPending(
                    userId = any(),
                    idempotencyKey = any(),
                    orderType = any(),
                    orderId = any(),
                    method = method,
                    amount = any(),
                    currency = any(),
                )
            } returns 1L

            every {
                paymentDomainService.initiatePg(any<PgInitiateCommand>())
            } returns PgInitiateResult(
                paymentId = 1L,
                status = PaymentStatus.FAILED,
                pgTransactionId = null,
                checkoutUrl = null,
            )

            every { paymentDomainService.getPayment(userId = 1L, paymentId = 1L) } returns failedPayment

            When("execute 를 호출하면") {
                val result = useCase.execute(buildCommand(method))

                Then("method=$method 로 FAILED 상태의 PaymentResponse 를 반환한다") {
                    result.status shouldBe PaymentStatus.FAILED
                    result.method shouldBe method
                }
            }
        }
    }
})
