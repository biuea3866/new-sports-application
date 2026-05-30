package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentGateway
import com.sportsapp.domain.payment.PaymentGatewayException
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldNotBeEmpty
import java.math.BigDecimal

class MockPgGatewayImplTest : BehaviorSpec({

    fun buildRequest(method: PaymentMethod, amount: BigDecimal = BigDecimal("10000")): PaymentRequest = PaymentRequest(
        idempotencyKey = "key-${System.nanoTime()}",
        method = method,
        amount = amount,
        currency = "KRW",
        orderType = OrderType.BOOKING,
        orderId = 1L,
    )

    Given("amount 가 0 이하인 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:9090")

        When("amount=0 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.KAKAO, BigDecimal.ZERO))
                }
            }
        }

        When("amount=-100 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.TOSS, BigDecimal("-100")))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 CREDIT_CARD 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-02] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.CREDIT_CARD))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 NAVER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-03] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.NAVER))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 DANAL 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-04] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.DANAL))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 BANK_TRANSFER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-05] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.requestPayment(buildRequest(PaymentMethod.BANK_TRANSFER))
                }
            }
        }
    }
})
