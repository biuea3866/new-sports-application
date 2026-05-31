package com.sportsapp.application.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.payment.PgInitiateCommand
import com.sportsapp.domain.payment.PgInitiateResult
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

class PreparePaymentUseCaseTest : BehaviorSpec({

    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = PreparePaymentUseCase(paymentDomainService)

    fun buildCommand(idempotencyKey: String = "prepare-key-01") = PreparePaymentCommand(
        userId = 1L,
        idempotencyKey = idempotencyKey,
        orderType = OrderType.BOOKING,
        orderId = 10L,
        method = PaymentMethod.CREDIT_CARD,
        amount = BigDecimal("10000"),
        currency = "KRW",
        itemName = "테스트 예약",
        returnUrl = "http://return",
        failUrl = "http://fail",
    )

    Given("신규 결제 준비 요청이 들어올 때") {
        val idempotencyKey = "prepare-new-01"
        val savedPaymentId = 1L

        every {
            paymentDomainService.createPending(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
        } returns savedPaymentId

        every {
            paymentDomainService.initiatePg(any<PgInitiateCommand>())
        } returns PgInitiateResult(
            paymentId = savedPaymentId,
            status = PaymentStatus.READY,
            pgTransactionId = "MOCK_CARD_abc",
            checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc",
        )

        When("execute 를 호출하면") {
            val result = useCase.execute(buildCommand(idempotencyKey))

            Then("createPending 과 initiatePg 를 각 1회씩 호출하고 PreparePaymentResponse 를 반환한다") {
                result.paymentId shouldBe savedPaymentId
                result.checkoutUrl shouldBe "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_abc"
                result.pgTransactionId shouldBe "MOCK_CARD_abc"
                verify(exactly = 1) {
                    paymentDomainService.createPending(
                        userId = any(),
                        idempotencyKey = idempotencyKey,
                        orderType = any(),
                        orderId = any(),
                        method = any(),
                        amount = any(),
                        currency = any(),
                    )
                }
                verify(exactly = 1) { paymentDomainService.initiatePg(any<PgInitiateCommand>()) }
            }
        }
    }

    Given("PreparePaymentUseCase.execute 메서드에 @Transactional 이 없는지 확인") {
        When("execute 메서드의 어노테이션을 조회하면") {
            val method = PreparePaymentUseCase::class.java.getMethod(
                "execute",
                PreparePaymentCommand::class.java,
            )
            Then("@Transactional 어노테이션이 존재하지 않아야 한다 (PG 호출이 tx 경계 밖에 있음)") {
                val hasTransactional = method.isAnnotationPresent(Transactional::class.java)
                hasTransactional shouldBe false
            }
        }
    }
})
