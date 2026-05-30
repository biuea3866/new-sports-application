package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentGateway
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PgPrepareRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldNotBeEmpty
import java.math.BigDecimal

class MockPaymentGatewayTest : BehaviorSpec({

    fun buildRequest(amount: BigDecimal): PgPrepareRequest = PgPrepareRequest(
        provider = "card",
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

    Given("MockPaymentGateway (성공 케이스)") {
        val gateway: PaymentGateway = MockPaymentGateway(pgBaseUrl = "http://localhost:9090")

        When("양수 amount 로 prepare 를 요청하면") {
            val result = gateway.prepare(buildRequest(BigDecimal("10000")))

            Then("[U-02] tid 가 비어 있지 않고 checkoutUrl 이 반환된다") {
                result.tid.shouldNotBeEmpty()
                result.checkoutUrl.shouldNotBeEmpty()
                result.provider.shouldNotBeEmpty()
            }
        }
    }

    Given("MockPaymentGateway (amount 검증 케이스)") {
        val gateway: PaymentGateway = MockPaymentGateway(pgBaseUrl = "http://localhost:9090")

        When("amount=0 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.prepare(buildRequest(BigDecimal.ZERO))
                }
            }
        }

        When("amount=-100 으로 요청하면") {
            Then("[U-01] IllegalArgumentException 을 던진다") {
                shouldThrow<IllegalArgumentException> {
                    gateway.prepare(buildRequest(BigDecimal("-100")))
                }
            }
        }
    }
})
