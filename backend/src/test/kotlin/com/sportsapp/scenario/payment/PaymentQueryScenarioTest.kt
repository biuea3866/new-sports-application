package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.math.BigDecimal
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class PaymentQueryScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun saveCompletedPayment(userId: Long, idempotencyKey: String): Payment {
        val payment = Payment.create(
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 10L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also {
            it.markReady("txn-query-$idempotencyKey", "card", "http://checkout")
            it.markCompleted(ZonedDateTime.now())
        }
        return paymentRepository.save(payment)
    }

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("userId = 1 의 COMPLETED 결제가 2건 존재하는 상태") {
            val userId = 1L
            saveCompletedPayment(userId, "idem-s01-1")
            saveCompletedPayment(userId, "idem-s01-2")

            When("GET /payments/me?status=COMPLETED 요청 시") {
                val response = mockMvc.get("/payments/me") {
                    header("X-User-Id", userId.toString())
                    param("status", "COMPLETED")
                    accept = MediaType.APPLICATION_JSON
                }.andReturn()

                Then("[S-01] 200 응답과 본인 결제 2건이 페이지네이션으로 반환된다") {
                    response.response.status shouldBe 200
                    val body = response.response.contentAsString
                    body.contains("\"totalElements\":2") shouldBe true
                }
            }
        }

        Given("userId = 10 의 결제가 존재하는 상태") {
            val ownerId = 10L
            val payment = saveCompletedPayment(ownerId, "idem-s02-owner")

            When("userId = 99 가 타인 paymentId 단건 조회 시") {
                val response = mockMvc.get("/payments/${payment.id}") {
                    header("X-User-Id", "99")
                    accept = MediaType.APPLICATION_JSON
                }.andReturn()

                Then("[S-02] 403 응답이 반환된다") {
                    response.response.status shouldBe 403
                }
            }
        }

        Given("userId = 5 의 COMPLETED 결제 1건이 존재하는 상태") {
            val userId = 5L
            val payment = saveCompletedPayment(userId, "idem-s03-own")

            When("GET /payments/{id} 본인 결제 단건 조회 시") {
                val response = mockMvc.get("/payments/${payment.id}") {
                    header("X-User-Id", userId.toString())
                    accept = MediaType.APPLICATION_JSON
                }.andReturn()

                Then("[S-03] 200 응답과 결제 정보가 반환된다") {
                    response.response.status shouldBe 200
                    val body = response.response.contentAsString
                    body.contains("\"status\":\"COMPLETED\"") shouldBe true
                }
            }
        }
    }
}
