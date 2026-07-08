package com.sportsapp.application.payment

import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.application.payment.dto.ConfirmPaymentWebhookCommand
import com.sportsapp.application.payment.usecase.ConfirmPaymentWebhookUseCase
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.math.BigDecimal
import java.time.ZonedDateTime

class ConfirmPaymentWebhookUseCaseTest : BehaviorSpec({

    fun buildCompletedResult(tid: String): ConfirmWebhookResult = ConfirmWebhookResult(
        id = 1L,
        orderType = OrderType.BOOKING,
        orderId = 100L,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("10000"),
        currency = "KRW",
        status = PaymentStatus.COMPLETED,
        pgTransactionId = tid,
        checkoutUrl = "http://checkout",
        paidAt = ZonedDateTime.now(),
        createdAt = ZonedDateTime.now(),
    )

    Given("DomainService가 DataIntegrityViolationException을 던질 때") {
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = ConfirmPaymentWebhookUseCase(paymentDomainService)
        val tid = "MOCK_CARD_concurrent_dive01"
        val completedResult = buildCompletedResult(tid)

        every {
            paymentDomainService.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")
        } throws DataIntegrityViolationException("unique constraint violation")

        every {
            paymentDomainService.findByPgTransactionIdOrThrow(tid)
        } returns completedResult

        When("execute를 호출하면") {
            val result = useCase.execute(ConfirmPaymentWebhookCommand(tid = tid, eventType = "PAYMENT_APPROVED"))

            Then("findByPgTransactionIdOrThrow로 재조회한 결과의 상태가 반환된다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 1) { paymentDomainService.findByPgTransactionIdOrThrow(tid) }
            }
        }
    }

    Given("DomainService가 ObjectOptimisticLockingFailureException을 던질 때") {
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = ConfirmPaymentWebhookUseCase(paymentDomainService)
        val tid = "MOCK_CARD_concurrent_olfe01"
        val completedResult = buildCompletedResult(tid)

        every {
            paymentDomainService.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")
        } throws ObjectOptimisticLockingFailureException(Payment::class.java, 1L)

        every {
            paymentDomainService.findByPgTransactionIdOrThrow(tid)
        } returns completedResult

        When("execute를 호출하면") {
            val result = useCase.execute(ConfirmPaymentWebhookCommand(tid = tid, eventType = "PAYMENT_APPROVED"))

            Then("findByPgTransactionIdOrThrow로 재조회한 결과의 상태가 반환된다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 1) { paymentDomainService.findByPgTransactionIdOrThrow(tid) }
            }
        }
    }

    Given("DomainService가 정상 처리하여 COMPLETED 상태의 결과를 반환할 때") {
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = ConfirmPaymentWebhookUseCase(paymentDomainService)
        val tid = "MOCK_CARD_early_return01"
        val completedResult = buildCompletedResult(tid)

        every {
            paymentDomainService.confirmWebhook(tid = tid, eventType = "PAYMENT_APPROVED")
        } returns completedResult

        When("execute를 호출하면") {
            val result = useCase.execute(ConfirmPaymentWebhookCommand(tid = tid, eventType = "PAYMENT_APPROVED"))

            Then("재조회 없이 confirmWebhook이 반환한 결과의 상태가 그대로 반환된다") {
                result.status shouldBe PaymentStatus.COMPLETED
                verify(exactly = 0) { paymentDomainService.findByPgTransactionIdOrThrow(any()) }
            }
        }
    }
})
