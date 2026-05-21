package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentRepositoryTest(
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("동일 idempotencyKey 로 두 건을 저장하면") {
            val key = "idem-dup-01"
            val first = Payment.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
            paymentRepository.save(first)

            val second = Payment.create(
                userId = 2L,
                idempotencyKey = key,
                orderType = OrderType.BOOKING,
                orderId = 101L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )

            Then("[R-01] DataIntegrityViolationException 이 발생한다") {
                shouldThrow<DataIntegrityViolationException> {
                    paymentRepository.save(second)
                }
            }
        }

        Given("idempotencyKey 로 조회") {
            val key = "idem-find-01"
            val payment = Payment.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.TICKETING,
                orderId = 200L,
                method = PaymentMethod.BANK_TRANSFER,
                amount = BigDecimal("15000"),
                currency = "KRW",
            )
            paymentRepository.save(payment)

            When("findByIdempotencyKey 를 호출하면") {
                val found = paymentRepository.findByIdempotencyKey(key)
                Then("[R-02] 저장된 Payment 가 반환된다") {
                    found.shouldNotBeNull()
                    found.idempotencyKey shouldBe key
                }
            }
        }

        Given("COMPLETED 상태 Payment 의 paidAt 을 저장하면") {
            val key = "idem-zdt-01"
            val originalPaidAt = ZonedDateTime.now()
            val payment = Payment.create(
                userId = 1L,
                idempotencyKey = key,
                orderType = OrderType.GOODS,
                orderId = 300L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("25000"),
                currency = "KRW",
            ).also { it.markCompleted(originalPaidAt) }

            val saved = paymentRepository.save(payment)

            When("findById 로 다시 조회하면") {
                val found = paymentRepository.findById(saved.id)
                Then("[R-03] paidAt 이 UTC instant 기준으로 동일하게 복원된다") {
                    found.shouldNotBeNull()
                    val paidAt = requireNotNull(found.paidAt)
                    paidAt.toInstant() shouldBe originalPaidAt.toInstant()
                }
            }
        }
    }
}
