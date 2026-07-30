package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다. */
@AutoConfigureMockMvc
class PreparePaymentTransactionSeparationScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val paymentDomainService: PaymentDomainService,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    private fun prepareRequestBody(method: String = "CREDIT_CARD") = """
        {
            "orderType": "BOOKING",
            "orderId": 1,
            "method": "$method",
            "amount": "10000",
            "currency": "KRW",
            "itemName": "테스트 예약",
            "returnUrl": "http://return",
            "failUrl": "http://fail"
        }
    """.trimIndent()

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("POST /payments/prepare 요청이 정상적으로 들어올 때") {
            val idempotencyKey = "scenario-prepare-ok-01"

            When("결제 준비를 요청하면") {
                val response = mockMvc.post("/payments/prepare") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = prepareRequestBody()
                }.andExpect {
                    status { isCreated() }
                }.andReturn()

                Then("DB 에 READY 상태의 Payment 가 1건 저장되고 checkoutUrl 이 반환된다") {
                    val status = jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE idempotency_key = ?",
                        String::class.java,
                        idempotencyKey,
                    )
                    status shouldBe PaymentStatus.READY.name

                    val responseBody = response.response.contentAsString
                    responseBody.contains("checkoutUrl") shouldBe true
                    responseBody.contains("paymentId") shouldBe true
                }
            }
        }

        Given("동일 idempotencyKey 로 POST /payments/prepare 를 2회 호출하면") {
            val idempotencyKey = "scenario-prepare-idem-01"

            When("같은 키로 2회 요청하면") {
                val firstResponse = mockMvc.post("/payments/prepare") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = prepareRequestBody()
                }.andReturn()

                val secondResponse = mockMvc.post("/payments/prepare") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = prepareRequestBody()
                }.andReturn()

                Then("DB 에 Payment 가 1건만 존재한다 (멱등)") {
                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE idempotency_key = ?",
                        Long::class.java,
                        idempotencyKey,
                    )
                    count shouldBe 1L

                    val firstPaymentId = extractPaymentId(firstResponse.response.contentAsString)
                    val secondPaymentId = extractPaymentId(secondResponse.response.contentAsString)
                    firstPaymentId shouldBe secondPaymentId
                }
            }
        }

        Given("PaymentDomainService 에 createPending 과 initiatePg 메서드가 존재할 때") {
            When("빈이 주입되면") {
                Then("PaymentDomainService 가 정상 wiring 되고 신규 메서드가 존재한다") {
                    paymentDomainService.shouldNotBeNull()
                    val hasCreatePending = PaymentDomainService::class.java
                        .methods
                        .any { it.name == "createPending" }
                    val hasInitiatePg = PaymentDomainService::class.java
                        .methods
                        .any { it.name == "initiatePg" }
                    hasCreatePending shouldBe true
                    hasInitiatePg shouldBe true
                }
            }
        }
    }

    private fun extractPaymentId(responseBody: String): Long {
        val regex = """"paymentId"\s*:\s*(\d+)""".toRegex()
        return regex.find(responseBody)?.groupValues?.get(1)?.toLong()
            ?: error("paymentId not found in: $responseBody")
    }
}
