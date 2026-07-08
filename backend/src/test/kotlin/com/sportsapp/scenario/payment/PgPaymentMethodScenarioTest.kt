package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@AutoConfigureMockMvc
class PgPaymentMethodScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        listOf("KAKAO", "TOSS", "NAVER", "DANAL", "BANK_TRANSFER", "CREDIT_CARD").forEach { method ->
            Given("method=$method 로 POST /payments 요청") {
                When("결제를 생성하면") {
                    val idempotencyKey = "pg-method-$method-01"
                    val requestBody = """
                        {
                            "orderType": "BOOKING",
                            "orderId": 1,
                            "method": "$method",
                            "amount": "20000",
                            "currency": "KRW"
                        }
                    """.trimIndent()

                    val response = mockMvc.post("/payments") {
                        contentType = MediaType.APPLICATION_JSON
                        header("Idempotency-Key", idempotencyKey)
                        content = requestBody
                    }.andExpect {
                        status { isCreated() }
                    }.andReturn()

                    Then("[S-01] method=$method 결제가 DB에 저장되고 method 컬럼이 일치한다") {
                        val savedMethod = jdbcTemplate.queryForObject(
                            "SELECT method FROM payments WHERE idempotency_key = ?",
                            String::class.java,
                            idempotencyKey,
                        )
                        savedMethod shouldBe method
                    }
                }
            }
        }

        Given("method=KAKAO 로 동일 idempotencyKey 두 번 요청") {
            val idempotencyKey = "pg-idem-kakao-01"
            val requestBody = """
                {
                    "orderType": "BOOKING",
                    "orderId": 1,
                    "method": "KAKAO",
                    "amount": "30000",
                    "currency": "KRW"
                }
            """.trimIndent()

            When("같은 키로 두 번 요청하면") {
                mockMvc.post("/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    content = requestBody
                }
                mockMvc.post("/payments") {
                    contentType = MediaType.APPLICATION_JSON
                    header("Idempotency-Key", idempotencyKey)
                    content = requestBody
                }

                Then("[S-02] DB에 KAKAO 결제 1건만 존재한다 (멱등)") {
                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE idempotency_key = ?",
                        Long::class.java,
                        idempotencyKey,
                    )
                    count shouldBe 1L
                }
            }
        }

        Given("유효하지 않은 method 로 POST /payments 요청") {
            When("method=INVALID_METHOD 로 요청하면") {
                Then("[S-03] 2xx 가 아닌 응답이 반환된다") {
                    mockMvc.post("/payments") {
                        contentType = MediaType.APPLICATION_JSON
                        header("Idempotency-Key", "pg-invalid-method-01")
                        content = """
                            {
                                "orderType": "BOOKING",
                                "orderId": 1,
                                "method": "INVALID_METHOD",
                                "amount": "10000",
                                "currency": "KRW"
                            }
                        """.trimIndent()
                    }.andReturn().let { result ->
                        val statusCode = result.response.status
                        (statusCode >= 400) shouldBe true
                    }
                }
            }
        }
    }
}
