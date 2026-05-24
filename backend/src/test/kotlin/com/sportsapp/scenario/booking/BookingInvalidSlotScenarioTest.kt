package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/**
 * DEF-010: POST /bookings — 존재하지 않는 slotId 입력 시 500이 아닌 404를 반환해야 한다.
 *
 * 결함 재현 조건: DB에 존재하지 않는 slotId (99999)로 요청 시 GlobalExceptionHandler가
 * ResourceNotFoundException을 처리하지 못하고 500을 반환한다.
 */
@AutoConfigureMockMvc
class BookingInvalidSlotScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE payments")
        }

        Given("[DEF-010] 존재하지 않는 slotId로 예약 요청") {
            val nonExistentSlotId = 99999L

            When("POST /bookings에 slotId=$nonExistentSlotId 로 요청하면") {
                val result = mockMvc.post("/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", "1")
                    content = """
                        {
                            "slotId": $nonExistentSlotId,
                            "paymentMethod": "CREDIT_CARD",
                            "amount": "10000",
                            "currency": "KRW"
                        }
                    """.trimIndent()
                }.andReturn()

                Then("[DEF-010] 500이 아닌 404 Not Found가 반환된다") {
                    result.response.status shouldBe 404
                }
            }
        }
    }
}
