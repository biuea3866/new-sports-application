package com.sportsapp.application.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
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
            val completedPayment = buildMockPayment(method, PaymentStatus.COMPLETED)
            every {
                paymentDomainService.create(
                    userId = any(),
                    idempotencyKey = any(),
                    orderType = any(),
                    orderId = any(),
                    method = method,
                    amount = any(),
                    currency = any(),
                )
            } returns completedPayment

            When("execute 를 호출하면") {
                val result = useCase.execute(buildCommand(method))

                Then("[U-01] method=$method 로 COMPLETED 상태의 PaymentResponse 를 반환한다") {
                    result.status shouldBe PaymentStatus.COMPLETED
                    result.method shouldBe method
                }
            }
        }

        Given("method=$method 로 결제 요청 — PG 실패") {
            val failedPayment = buildMockPayment(method, PaymentStatus.FAILED)
            every {
                paymentDomainService.create(
                    userId = any(),
                    idempotencyKey = any(),
                    orderType = any(),
                    orderId = any(),
                    method = method,
                    amount = any(),
                    currency = any(),
                )
            } returns failedPayment

            When("execute 를 호출하면") {
                val result = useCase.execute(buildCommand(method))

                Then("[U-02] method=$method 로 FAILED 상태의 PaymentResponse 를 반환한다") {
                    result.status shouldBe PaymentStatus.FAILED
                    result.method shouldBe method
                }
            }
        }
    }
})
