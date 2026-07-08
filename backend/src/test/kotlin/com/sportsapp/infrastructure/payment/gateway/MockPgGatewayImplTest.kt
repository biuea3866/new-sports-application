package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.exception.PaymentGatewayException
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.vo.toPgProviderName
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import java.math.BigDecimal

class MockPgGatewayImplTest : BehaviorSpec({

    fun buildRequest(method: PaymentMethod, amount: BigDecimal = BigDecimal("10000")): PgPrepareRequest = PgPrepareRequest(
        provider = method.toPgProviderName(),
        idempotencyKey = "key-${System.nanoTime()}",
        userId = 1L,
        orderType = OrderType.BOOKING,
        orderId = 1L,
        amount = amount,
        currency = "KRW",
        itemName = "테스트 상품",
        returnUrl = "http://localhost/return",
        failUrl = "http://localhost/fail",
    )

    Given("amount 가 0 이하인 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:9090")

        When("amount=0 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.prepare(buildRequest(PaymentMethod.KAKAO, BigDecimal.ZERO))
                }
            }
        }

        When("amount=-100 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.prepare(buildRequest(PaymentMethod.TOSS, BigDecimal("-100")))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 CREDIT_CARD 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-02] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.CREDIT_CARD))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 NAVER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-03] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.NAVER))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 BANK_TRANSFER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-05] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.BANK_TRANSFER))
                }
            }
        }
    }
})
