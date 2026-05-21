package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentGateway
import com.sportsapp.domain.payment.PaymentGatewayException
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.math.BigDecimal

class MockPaymentGatewayTest : BehaviorSpec({

    fun buildRequest(amount: BigDecimal): PaymentRequest = PaymentRequest(
        idempotencyKey = "key-${System.nanoTime()}",
        method = PaymentMethod.CREDIT_CARD,
        amount = amount,
        currency = "KRW",
        orderType = OrderType.BOOKING,
        orderId = 1L,
    )

    Given("success-rate=1.0 인 MockPaymentGateway (성공 케이스)") {
        val gateway: PaymentGateway = MockPaymentGateway(successRate = 1.0)

        When("양수 amount 로 요청하면") {
            val result = gateway.requestPayment(buildRequest(BigDecimal("10000")))

            Then("[U-02] 항상 성공 결과를 반환한다") {
                result.pgTransactionId.shouldNotBeEmpty()
            }
        }
    }

    Given("success-rate=0.0 인 MockPaymentGateway") {
        val gateway: PaymentGateway = MockPaymentGateway(successRate = 0.0)

        When("양수 amount 로 요청하면") {
            Then("[U-02] 항상 실패(PaymentGatewayException)를 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.requestPayment(buildRequest(BigDecimal("10000")))
                }
            }
        }
    }

    Given("success-rate=1.0 인 MockPaymentGateway (amount 검증 케이스)") {
        val gateway: PaymentGateway = MockPaymentGateway(successRate = 1.0)

        When("amount=0 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.requestPayment(buildRequest(BigDecimal.ZERO))
                }
            }
        }

        When("amount=-100 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.requestPayment(buildRequest(BigDecimal("-100")))
                }
            }
        }
    }

    Given("success-rate=1.0 인 MockPaymentGateway (지연 측정 케이스)") {
        val gateway: PaymentGateway = MockPaymentGateway(successRate = 1.0)

        When("요청을 처리하면") {
            val startTime = System.currentTimeMillis()
            gateway.requestPayment(buildRequest(BigDecimal("5000")))
            val elapsed = System.currentTimeMillis() - startTime

            Then("[U-04] 인위적 지연이 50ms 이상 발생한다") {
                elapsed shouldBeGreaterThanOrEqual 50L
            }
        }
    }

    Given("success-rate=0.5 인 MockPaymentGateway") {
        val gateway: PaymentGateway = MockPaymentGateway(successRate = 0.5)

        When("100회 호출하면") {
            var successCount = 0
            repeat(100) {
                runCatching { gateway.requestPayment(buildRequest(BigDecimal("1000"))) }
                    .onSuccess { successCount++ }
            }

            Then("[U-03] 성공 횟수가 35~65건 범위 안에 든다") {
                (successCount in 35..65) shouldBe true
            }
        }
    }
})
