package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class OrderPaymentGatewayImplTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val gateway = OrderPaymentGatewayImpl(paymentDomainService)

    Given("유효한 결제 생성 파라미터") {
        val payment = mockk<Payment>()
        every { payment.id } returns 999L
        every { payment.status } returns PaymentStatus.READY

        every {
            paymentDomainService.create(
                userId = 1L,
                idempotencyKey = "idem-key",
                orderType = OrderType.TICKETING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("80000"),
                currency = "KRW",
            )
        } returns payment

        When("createPayment 를 호출하면") {
            val result = gateway.createPayment(
                userId = 1L,
                idempotencyKey = "idem-key",
                orderType = OrderType.TICKETING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("80000"),
                currency = "KRW",
            )

            Then("PaymentDomainService.create 에 동일한 파라미터를 전달하고 Payment 를 반환한다") {
                result shouldBe payment
                verify(exactly = 1) {
                    paymentDomainService.create(
                        userId = 1L,
                        idempotencyKey = "idem-key",
                        orderType = OrderType.TICKETING,
                        orderId = 100L,
                        method = PaymentMethod.CREDIT_CARD,
                        amount = BigDecimal("80000"),
                        currency = "KRW",
                    )
                }
            }
        }
    }

    Given("GOODS 타입 결제 생성 파라미터") {
        val payment = mockk<Payment>()
        every { payment.id } returns 50L
        every { payment.status } returns PaymentStatus.READY

        every {
            paymentDomainService.create(
                userId = 2L,
                idempotencyKey = "idem-goods",
                orderType = OrderType.GOODS,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("30000"),
                currency = "KRW",
            )
        } returns payment

        When("createPayment 를 호출하면") {
            val result = gateway.createPayment(
                userId = 2L,
                idempotencyKey = "idem-goods",
                orderType = OrderType.GOODS,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("30000"),
                currency = "KRW",
            )

            Then("GOODS 타입으로 PaymentDomainService.create 가 1회 호출된다") {
                result shouldBe payment
                verify(exactly = 1) {
                    paymentDomainService.create(
                        userId = 2L,
                        idempotencyKey = "idem-goods",
                        orderType = OrderType.GOODS,
                        orderId = 200L,
                        method = PaymentMethod.CREDIT_CARD,
                        amount = BigDecimal("30000"),
                        currency = "KRW",
                    )
                }
            }
        }
    }
})
