package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentRepository
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentCustomRepositoryImplTest(
    @Autowired private val paymentRepository: PaymentRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun buildPayment(
        userId: Long,
        idempotencyKey: String,
        status: PaymentStatus = PaymentStatus.PENDING,
    ): Payment {
        val payment = Payment.create(
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.BOOKING,
            orderId = 10L,
            method = PaymentMethod.CREDIT_CARD,
            amount = BigDecimal("10000"),
            currency = "KRW",
        )
        if (status == PaymentStatus.COMPLETED) payment.markCompleted(ZonedDateTime.now(), "txn-${idempotencyKey}", "card")
        if (status == PaymentStatus.FAILED) payment.markFailed("PG error")
        return paymentRepository.save(payment)
    }

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("userId = 1 의 COMPLETED 결제 2건과 FAILED 결제 1건이 저장된 상태") {
            val userId = 1L
            buildPayment(userId, "idem-r01-1", PaymentStatus.COMPLETED)
            buildPayment(userId, "idem-r01-2", PaymentStatus.COMPLETED)
            buildPayment(userId, "idem-r01-3", PaymentStatus.FAILED)

            val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))

            When("status = COMPLETED 조건으로 조회하면") {
                val result = paymentRepository.findByUserIdAndConditions(
                    userId = userId,
                    status = PaymentStatus.COMPLETED,
                    paidAtFrom = null,
                    paidAtTo = null,
                    pageable = pageable,
                )

                Then("[R-01] COMPLETED 결제 2건만 반환된다") {
                    result.totalElements shouldBe 2
                    result.content.all { it.status == PaymentStatus.COMPLETED } shouldBe true
                }
            }
        }

        Given("userId = 2 의 결제 3건이 저장된 상태") {
            val userId = 2L
            buildPayment(userId, "idem-r02-1", PaymentStatus.COMPLETED)
            buildPayment(userId, "idem-r02-2", PaymentStatus.COMPLETED)
            buildPayment(userId, "idem-r02-3", PaymentStatus.COMPLETED)

            val pageable = PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "createdAt"))

            When("pageSize = 2 로 조회하면") {
                val page0 = paymentRepository.findByUserIdAndConditions(
                    userId = userId,
                    status = null,
                    paidAtFrom = null,
                    paidAtTo = null,
                    pageable = pageable,
                )

                Then("[R-02] totalElements = 3, 첫 페이지 content = 2건이 반환된다") {
                    page0.totalElements shouldBe 3
                    page0.content.size shouldBe 2
                    page0.totalPages shouldBe 2
                }
            }
        }

        Given("userId = 1 의 결제와 userId = 3 의 결제가 각각 존재하는 상태") {
            val userOneId = 1L
            val userThreeId = 3L
            buildPayment(userOneId, "idem-r03-u1", PaymentStatus.COMPLETED)
            buildPayment(userThreeId, "idem-r03-u3", PaymentStatus.COMPLETED)

            val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))

            When("userId = 1 조건으로 조회하면") {
                val result = paymentRepository.findByUserIdAndConditions(
                    userId = userOneId,
                    status = null,
                    paidAtFrom = null,
                    paidAtTo = null,
                    pageable = pageable,
                )

                Then("[R-03] userId = 1 의 결제만 반환되고 userId = 3 결제는 포함되지 않는다") {
                    result.totalElements shouldBe 1
                    result.content.all { it.userId == userOneId } shouldBe true
                }
            }
        }

        Given("userId = 1 의 paidAt 이 설정된 COMPLETED 결제가 존재하는 상태") {
            val userId = 1L
            val paidAt = ZonedDateTime.now()
            val payment = Payment.create(
                userId = userId,
                idempotencyKey = "idem-r04-paidat",
                orderType = OrderType.BOOKING,
                orderId = 10L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            ).also { it.markCompleted(paidAt, "txn-r04", "card") }
            paymentRepository.save(payment)

            val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))

            When("paidAtFrom = paidAt - 1일, paidAtTo = paidAt + 1일 범위로 조회하면") {
                val result = paymentRepository.findByUserIdAndConditions(
                    userId = userId,
                    status = null,
                    paidAtFrom = paidAt.minusDays(1),
                    paidAtTo = paidAt.plusDays(1),
                    pageable = pageable,
                )

                Then("[R-04] paidAt 범위 내 결제 1건이 반환된다") {
                    result.totalElements shouldBe 1
                    result.content.first().paidAt shouldNotBe null
                }
            }

            When("paidAtFrom = paidAt + 1일 (범위 밖) 조건으로 조회하면") {
                val result = paymentRepository.findByUserIdAndConditions(
                    userId = userId,
                    status = null,
                    paidAtFrom = paidAt.plusDays(1),
                    paidAtTo = null,
                    pageable = pageable,
                )

                Then("[R-05] 범위 밖이므로 결제 0건이 반환된다") {
                    result.totalElements shouldBe 0
                }
            }
        }
    }
}
