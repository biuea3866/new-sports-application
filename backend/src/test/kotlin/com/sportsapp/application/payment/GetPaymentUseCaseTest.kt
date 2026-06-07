package com.sportsapp.application.payment

import com.sportsapp.domain.payment.exception.NotPaymentOwnerException
import com.sportsapp.application.payment.usecase.GetPaymentUseCase
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetPaymentUseCaseTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = GetPaymentUseCase(paymentDomainService)

    fun buildMockPayment(userId: Long, paymentId: Long = 1L): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns paymentId
        every { payment.userId } returns userId
        every { payment.orderType } returns OrderType.BOOKING
        every { payment.orderId } returns 10L
        every { payment.method } returns PaymentMethod.CREDIT_CARD
        every { payment.amount } returns BigDecimal("10000")
        every { payment.status } returns PaymentStatus.COMPLETED
        every { payment.createdAt } returns ZonedDateTime.now()
        every { payment.paidAt } returns ZonedDateTime.now()
        every { payment.checkoutUrl } returns null
        return payment
    }

    Given("본인 paymentId 가 아닌 경우") {
        val requestingUserId = 1L
        val paymentId = 42L

        every { paymentDomainService.getPayment(requestingUserId, paymentId) } throws
            NotPaymentOwnerException(paymentId)

        When("execute 를 호출하면") {
            Then("[U-01] NotPaymentOwnerException 을 던진다") {
                shouldThrow<NotPaymentOwnerException> {
                    useCase.execute(userId = requestingUserId, paymentId = paymentId)
                }
            }
        }
    }

    Given("본인 paymentId 인 경우") {
        val userId = 1L
        val paymentId = 10L
        val payment = buildMockPayment(userId = userId, paymentId = paymentId)

        every { paymentDomainService.getPayment(userId, paymentId) } returns payment

        When("execute 를 호출하면") {
            val result = useCase.execute(userId = userId, paymentId = paymentId)

            Then("[U-02] PaymentResponse 를 반환한다") {
                result.status shouldBe PaymentStatus.COMPLETED
                result.amount shouldBe BigDecimal("10000")
            }
        }
    }
})
