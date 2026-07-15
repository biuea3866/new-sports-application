package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** AUTH-04 — `POST /payments`는 이전에 `userId = 1L` 하드코딩 버그가 있었다. JWT principal로 수정하며 테스트도 실 토큰을 싣는다. */
@AutoConfigureMockMvc
class PaymentCreateScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
) : BaseIntegrationTest() {

    private val baseRequestBody = """
        {
            "orderType": "BOOKING",
            "orderId": 1,
            "method": "CREDIT_CARD",
            "amount": "10000",
            "currency": "KRW"
        }
    """.trimIndent()

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("동일 Idempotency-Key 로 두 번 POST /payments 를 호출하면") {
            val idempotencyKey = "scenario-idem-01"

            When("첫 번째 요청") {
                val firstResponse = mockMvc.post("/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = baseRequestBody
                }.andReturn()

                val firstPaymentId = extractPaymentId(firstResponse.response.contentAsString)

                Then("[S-01] 두 번째 동일 키 요청 시 동일 paymentId 반환 + DB 1건만 존재한다") {
                    val secondResponse = mockMvc.post("/payments") {
                        contentType = MediaType.APPLICATION_JSON
                        header("Idempotency-Key", idempotencyKey)
                        header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                        content = baseRequestBody
                    }.andReturn()

                    val secondPaymentId = extractPaymentId(secondResponse.response.contentAsString)
                    firstPaymentId shouldBe secondPaymentId

                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE idempotency_key = ?",
                        Long::class.java,
                        idempotencyKey,
                    )
                    count shouldBe 1L
                }
            }
        }

        Given("서로 다른 Idempotency-Key 로 두 번 POST /payments 를 호출하면") {
            val firstKey = "scenario-diff-01"
            val secondKey = "scenario-diff-02"

            When("서로 다른 두 요청을 순서대로 전송하면") {
                val firstResponse = mockMvc.post("/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", firstKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = baseRequestBody
                }.andReturn()

                val secondResponse = mockMvc.post("/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", secondKey)
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                    content = baseRequestBody
                }.andReturn()

                val firstPaymentId = extractPaymentId(firstResponse.response.contentAsString)
                val secondPaymentId = extractPaymentId(secondResponse.response.contentAsString)

                Then("[S-02] 서로 다른 paymentId 를 가진 두 Payment row 가 생성된다") {
                    firstPaymentId shouldNotBe secondPaymentId

                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE idempotency_key IN (?, ?)",
                        Long::class.java,
                        firstKey,
                        secondKey,
                    )
                    count shouldBe 2L
                }
            }
        }

        Given("음수 amount 로 POST /payments 를 호출하면") {
            val invalidBody = """
                {
                    "orderType": "BOOKING",
                    "orderId": 1,
                    "method": "CREDIT_CARD",
                    "amount": "-100",
                    "currency": "KRW"
                }
            """.trimIndent()

            When("amount = -100 요청을 전송하면") {
                Then("[S-03] 400 Bad Request 응답이 반환된다 (bean-validation 위반)") {
                    mockMvc.post("/payments") {
                        contentType = MediaType.APPLICATION_JSON
                        header("Idempotency-Key", "scenario-invalid-01")
                        header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(1L))
                        content = invalidBody
                    }.andExpect {
                        status { isBadRequest() }
                    }
                }
            }
        }
    }

    private fun extractPaymentId(responseBody: String): Long {
        val regex = """"id"\s*:\s*(\d+)""".toRegex()
        return regex.find(responseBody)?.groupValues?.get(1)?.toLong()
            ?: error("paymentId not found in response: $responseBody")
    }
}
