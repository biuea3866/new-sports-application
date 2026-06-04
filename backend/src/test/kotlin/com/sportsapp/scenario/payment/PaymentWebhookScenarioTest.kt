package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.payment.gateway.OrderConfirmationGateway
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.infrastructure.messaging.KafkaDomainEventPublisher
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

@TestConfiguration
class PaymentWebhookTestConfig {
    /**
     * KafkaDomainEventPublisher 를 mockk 로 대체 — 실제 Kafka 브로커 없이 webhook 시나리오 실행.
     * allow-bean-definition-overriding=true 로 등록된 기존 bean 을 덮어씀.
     */
    @Bean
    fun kafkaDomainEventPublisher(): KafkaDomainEventPublisher = mockk(relaxed = true)

    /**
     * OrderConfirmationGatewayImpl 대신 no-op 구현을 등록.
     * 테스트에 실제 Booking/Goods/Ticketing 데이터가 없으므로 confirm/cancel 을 무시한다.
     */
    @Bean
    @Primary
    fun orderConfirmationGateway(): OrderConfirmationGateway = object : OrderConfirmationGateway {
        override fun confirm(orderType: OrderType, orderId: Long, paymentId: Long) {}
        override fun cancel(orderType: OrderType, orderId: Long, paymentId: Long) {}
    }
}

@AutoConfigureMockMvc
@Import(PaymentWebhookTestConfig::class)
class PaymentWebhookScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("READY 상태 Payment 가 존재할 때 PAYMENT_APPROVED webhook 수신") {
            val tid = "MOCK_CARD_webhook_approve01"
            saveReadyPayment(tid = tid, idempotencyKey = "webhook-scenario-01")

            When("POST /payments/webhook 을 한 번 호출하면") {
                mockMvc.post("/payments/webhook") {
                    contentType = MediaType.APPLICATION_JSON
                    content = buildWebhookBody(eventType = "PAYMENT_APPROVED", tid = tid)
                }.andExpect { status { isOk() } }

                Then("DB 상태가 COMPLETED 로 변경된다") {
                    val status = jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE pg_transaction_id = ?",
                        String::class.java,
                        tid,
                    )
                    status shouldBe PaymentStatus.COMPLETED.name
                }
            }
        }

        Given("READY 상태 Payment 에 PAYMENT_APPROVED webhook 을 serial 2회 수신 (멱등)") {
            val tid = "MOCK_CARD_webhook_idem01"
            saveReadyPayment(tid = tid, idempotencyKey = "webhook-scenario-02")

            When("동일 tid 로 webhook 을 순차 2회 호출하면") {
                repeat(2) {
                    mockMvc.post("/payments/webhook") {
                        contentType = MediaType.APPLICATION_JSON
                        content = buildWebhookBody(eventType = "PAYMENT_APPROVED", tid = tid)
                    }.andExpect { status { isOk() } }
                }

                Then("상태는 COMPLETED 1회만 반영되고 DB row 는 1건이다") {
                    val status = jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE pg_transaction_id = ?",
                        String::class.java,
                        tid,
                    )
                    status shouldBe PaymentStatus.COMPLETED.name

                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE pg_transaction_id = ?",
                        Long::class.java,
                        tid,
                    )
                    count shouldBe 1L
                }
            }
        }

        Given("READY 상태 Payment 에 PAYMENT_APPROVED webhook 이 동시에 2건 도착 (동시성 낙관락)") {
            val tid = "MOCK_CARD_webhook_concurrent01"
            saveReadyPayment(tid = tid, idempotencyKey = "webhook-scenario-concurrent")

            When("두 스레드가 동시에 /payments/webhook 을 호출하면") {
                val latch = CountDownLatch(1)
                val executor = Executors.newFixedThreadPool(2)
                val statusCodes = mutableListOf<Int>()

                val futures = (1..2).map {
                    executor.submit<Int> {
                        latch.await()
                        mockMvc.post("/payments/webhook") {
                            contentType = MediaType.APPLICATION_JSON
                            content = buildWebhookBody(eventType = "PAYMENT_APPROVED", tid = tid)
                        }.andReturn().response.status
                    }
                }
                latch.countDown()
                futures.forEach { statusCodes.add(it.get()) }
                executor.shutdown()

                Then("DB 상태가 COMPLETED 로 정확히 1건 수렴한다 (낙관락이 이중 반영 차단)") {
                    // 두 트랜잭션 중 하나는 200(성공), 하나는 200(조기반환) 또는 409(OptimisticLock).
                    // 어느 경우든 COMPLETED row 는 반드시 1건이어야 한다.
                    val completedCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE pg_transaction_id = ? AND status = 'COMPLETED'",
                        Long::class.java,
                        tid,
                    )
                    completedCount shouldBe 1L
                }
            }
        }

        Given("READY 상태 Payment 에 PAYMENT_CANCELED webhook 수신") {
            val tid = "MOCK_CARD_webhook_cancel01"
            saveReadyPayment(tid = tid, idempotencyKey = "webhook-scenario-03")

            When("POST /payments/webhook 에 PAYMENT_CANCELED 를 전송하면") {
                mockMvc.post("/payments/webhook") {
                    contentType = MediaType.APPLICATION_JSON
                    content = buildWebhookBody(eventType = "PAYMENT_CANCELED", tid = tid)
                }.andExpect { status { isOk() } }

                Then("DB 상태가 CANCELLED 로 변경된다") {
                    val status = jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE pg_transaction_id = ?",
                        String::class.java,
                        tid,
                    )
                    status shouldBe PaymentStatus.CANCELLED.name
                }
            }
        }
    }

    private fun saveReadyPayment(tid: String, idempotencyKey: String): Payment {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 999L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also { it.markReady(tid, "card", "http://localhost:9090/pg/card/checkout?tid=$tid") }
        return paymentRepository.save(payment)
    }

    private fun buildWebhookBody(eventType: String, tid: String): String = """
        {
            "eventType": "$eventType",
            "provider": "card",
            "tid": "$tid",
            "orderId": "BOOKING_999",
            "amount": 10000,
            "status": "${if (eventType == "PAYMENT_APPROVED") "APPROVED" else "CANCELED"}",
            "timestamp": "2026-05-30T10:00:00Z"
        }
    """.trimIndent()
}
