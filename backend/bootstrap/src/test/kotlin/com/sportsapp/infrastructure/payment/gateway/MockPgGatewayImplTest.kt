package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.exception.PaymentGatewayException
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.vo.toPgProviderName
import com.sportsapp.infrastructure.external.ExternalContractSupport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import okhttp3.mockwebserver.MockResponse
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
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:9090", publicBaseUrl = "http://localhost:9090")

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
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090", publicBaseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-02] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.CREDIT_CARD))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 NAVER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090", publicBaseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-03] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.NAVER))
                }
            }
        }
    }

    Given("네트워크 없는 환경에서 BANK_TRANSFER 요청") {
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = "http://localhost:19090", publicBaseUrl = "http://localhost:19090")

        When("Mock PG 서버가 없는 포트로 요청하면") {
            Then("[U-05] PaymentGatewayException 을 던진다") {
                shouldThrow<PaymentGatewayException> {
                    gateway.prepare(buildRequest(PaymentMethod.BANK_TRANSFER))
                }
            }
        }
    }

    Given("서버 호출용 base-url 과 브라우저 공개용 base-url 이 다르게 설정된 경우") {
        val mockWebServer = ExternalContractSupport.startMockServer()
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("""{"tid":"mock-tid-123"}"""),
        )
        val internalBaseUrl = mockWebServer.url("/").toString().trimEnd('/')
        val publicBaseUrl = "http://localhost:9190"
        val gateway: PaymentGateway = MockPgGatewayImpl(baseUrl = internalBaseUrl, publicBaseUrl = publicBaseUrl)

        When("prepare 를 호출하면") {
            val result = gateway.prepare(buildRequest(PaymentMethod.KAKAO))

            Then("[U-06] PG ready 호출은 mock-base-url(내부망)로, checkoutUrl 은 public-base-url(브라우저 공개)로 조립된다") {
                val recordedRequest = mockWebServer.takeRequest()
                recordedRequest.path shouldBe "/pg/kakao/ready"
                result.checkoutUrl shouldStartWith publicBaseUrl
                result.checkoutUrl shouldBe "$publicBaseUrl/pg/kakao/checkout?tid=mock-tid-123"
            }
        }

        mockWebServer.shutdown()
    }
})
