package com.sportsapp.scenario.payment

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.application.payment.dto.ConfirmPaymentWebhookCommand
import com.sportsapp.application.payment.usecase.ConfirmPaymentWebhookUseCase
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * webhook 멱등 처리 시나리오 테스트.
 *
 * 주의: 동시 첫 도착(READY→COMPLETED 경쟁) 시나리오는 Kafka 인프라가 필요하므로
 * 단위 테스트(ConfirmPaymentWebhookUseCaseTest U-01/U-02)에서 Mock 수준으로 검증한다.
 * 본 클래스는 이미 COMPLETED된 Payment에 대한 재수신 멱등 처리를 통합 레벨에서 검증한다.
 */
class PaymentWebhookConcurrentIdempotencyScenarioTest(
    @Autowired private val confirmPaymentWebhookUseCase: ConfirmPaymentWebhookUseCase,
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("이미 COMPLETED 상태인 Payment에 PAYMENT_APPROVED 웹훅이 재수신될 때") {
            val tid = "MOCK_CARD_completed_idem_01"
            val completedPayment = saveCompletedPayment(tid = tid, idempotencyKey = "completed-idem-scenario-01")

            When("동일 tid로 UseCase를 호출하면") {
                val result = confirmPaymentWebhookUseCase.execute(
                    ConfirmPaymentWebhookCommand(
                        tid = tid,
                        eventType = "PAYMENT_APPROVED",
                    )
                )

                Then("COMPLETED 상태가 유지되고 DB row는 1건이다 (멱등 early-return 동작)") {
                    result.status shouldBe PaymentStatus.COMPLETED

                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE pg_transaction_id = ? AND status = 'COMPLETED'",
                        Long::class.java,
                        tid,
                    )
                    count shouldBe 1L
                }
            }
        }

        Given("이미 COMPLETED 상태인 Payment에 PAYMENT_APPROVED 웹훅이 순차 2회 수신될 때") {
            val tid = "MOCK_CARD_completed_idem_02"
            saveCompletedPayment(tid = tid, idempotencyKey = "completed-idem-scenario-02")

            When("동일 tid로 UseCase를 2회 순차 호출하면") {
                repeat(2) {
                    confirmPaymentWebhookUseCase.execute(
                        ConfirmPaymentWebhookCommand(
                            tid = tid,
                            eventType = "PAYMENT_APPROVED",
                        )
                    )
                }

                Then("두 번 호출해도 COMPLETED row는 1건이다 (멱등 처리)") {
                    val count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM payments WHERE pg_transaction_id = ? AND status = 'COMPLETED'",
                        Long::class.java,
                        tid,
                    )
                    count shouldBe 1L
                }
            }
        }
    }

    private fun saveCompletedPayment(tid: String, idempotencyKey: String): Payment {
        val payment = Payment.create(
            userId = 1L,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 999L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        ).also {
            it.markReady(tid, "card", "http://localhost:9090/pg/card/checkout?tid=$tid")
            it.markCompleted(ZonedDateTime.now())
            it.pullDomainEvents() // 이벤트 소비 (DB 저장 시 이벤트 없이 저장)
        }
        return paymentRepository.save(payment)
    }
}
