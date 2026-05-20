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
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.math.BigDecimal
import java.time.ZonedDateTime

class ListMyPaymentsUseCaseTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = ListMyPaymentsUseCase(paymentDomainService)

    fun buildMockPayment(userId: Long, status: PaymentStatus = PaymentStatus.COMPLETED): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns (System.nanoTime() % 10000)
        every { payment.userId } returns userId
        every { payment.orderType } returns OrderType.BOOKING
        every { payment.orderId } returns 10L
        every { payment.method } returns PaymentMethod.CREDIT_CARD
        every { payment.amount } returns BigDecimal("10000")
        every { payment.status } returns status
        every { payment.createdAt } returns ZonedDateTime.now()
        every { payment.paidAt } returns if (status == PaymentStatus.COMPLETED) ZonedDateTime.now() else null
        return payment
    }

    Given("userId = 1 이고 status = COMPLETED 조건으로 조회할 때") {
        val userId = 1L
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        val payments = listOf(buildMockPayment(userId), buildMockPayment(userId))

        every {
            paymentDomainService.findMyPayments(
                userId = userId,
                status = PaymentStatus.COMPLETED,
                paidAtFrom = null,
                paidAtTo = null,
                pageable = pageable,
            )
        } returns PageImpl(payments, pageable, 2)

        When("execute 를 호출하면") {
            val criteria = PaymentCriteria(
                userId = userId,
                status = PaymentStatus.COMPLETED,
                paidAtFrom = null,
                paidAtTo = null,
                pageable = pageable,
            )
            val result = useCase.execute(criteria)

            Then("[U-01] COMPLETED 결제 2건이 페이지 결과로 반환된다") {
                result.totalElements shouldBe 2
                result.content.size shouldBe 2
                result.content.all { it.status == PaymentStatus.COMPLETED } shouldBe true
            }
        }
    }

    Given("조건 없이 전체 조회할 때") {
        val userId = 2L
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
        val payments = listOf(
            buildMockPayment(userId, PaymentStatus.COMPLETED),
            buildMockPayment(userId, PaymentStatus.FAILED),
        )

        every {
            paymentDomainService.findMyPayments(
                userId = userId,
                status = null,
                paidAtFrom = null,
                paidAtTo = null,
                pageable = pageable,
            )
        } returns PageImpl(payments, pageable, 2)

        When("execute 를 호출하면") {
            val criteria = PaymentCriteria(
                userId = userId,
                status = null,
                paidAtFrom = null,
                paidAtTo = null,
                pageable = pageable,
            )
            val result = useCase.execute(criteria)

            Then("[U-02] 모든 상태의 결제 2건이 반환된다") {
                result.totalElements shouldBe 2
                result.content.size shouldBe 2
            }
        }
    }
})
