package com.sportsapp.infrastructure.payment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class PaymentPreparePhaseRepositoryTest(
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("PENDING 상태 Payment 를 저장한 뒤 findById 로 조회하면") {
            val idempotencyKey = "repo-pending-01"
            val payment = Payment.create(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            val saved = paymentRepository.save(payment)

            When("findById 를 호출하면") {
                val found = paymentRepository.findById(saved.id)

                Then("PENDING 상태로 저장되고 복원된다") {
                    found.shouldNotBeNull()
                    found.status shouldBe PaymentStatus.PENDING
                    found.idempotencyKey shouldBe idempotencyKey
                    found.pgTransactionId shouldBe null
                    found.checkoutUrl shouldBe null
                }
            }
        }

        Given("PENDING Payment 에 markReady 를 적용한 뒤 저장하면") {
            val idempotencyKey = "repo-ready-01"
            val tid = "MOCK_CARD_repo01"
            val checkoutUrl = "http://localhost:9090/pg/card/checkout?tid=MOCK_CARD_repo01"

            val payment = Payment.create(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 200L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("20000"),
                currency = "KRW",
            ).also { it.markReady(tid = tid, provider = "card", checkoutUrl = checkoutUrl) }
            val saved = paymentRepository.save(payment)

            When("findById 로 다시 조회하면") {
                val found = paymentRepository.findById(saved.id)

                Then("pgTransactionId, provider, checkoutUrl, status=READY 가 정확히 저장된다") {
                    found.shouldNotBeNull()
                    found.status shouldBe PaymentStatus.READY
                    found.pgTransactionId shouldBe tid
                    found.provider shouldBe "card"
                    found.checkoutUrl shouldBe checkoutUrl
                }
            }
        }

        Given("PENDING Payment 에 markFailed 를 적용한 뒤 저장하면") {
            val idempotencyKey = "repo-failed-01"
            val failureReason = "PG timeout after 5s"

            val payment = Payment.create(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.BOOKING,
                orderId = 300L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("15000"),
                currency = "KRW",
            ).also { it.markFailed(failureReason) }
            val saved = paymentRepository.save(payment)

            When("findById 로 다시 조회하면") {
                val found = paymentRepository.findById(saved.id)

                Then("status=FAILED, failureReason 이 저장된다") {
                    found.shouldNotBeNull()
                    found.status shouldBe PaymentStatus.FAILED
                    found.failureReason shouldBe failureReason
                }
            }
        }
    }
}
