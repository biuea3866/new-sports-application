package com.sportsapp.infrastructure.payment.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.vo.PaymentMethod
import io.kotest.assertions.throwables.shouldThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class PaymentConcurrencyRepositoryTest(
    @Autowired private val paymentJpaRepository: PaymentJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("동일 pg_transaction_id로 두 번 저장하면") {
            val tid = "MOCK_CARD_dup_pg_tid_01"
            paymentJpaRepository.save(
                Payment.create(
                    userId = 1L,
                    idempotencyKey = "pg-dup-key-01",
                    orderType = OrderType.BOOKING,
                    orderId = 100L,
                    method = PaymentMethod.CREDIT_CARD,
                    amount = BigDecimal("10000"),
                    currency = "KRW",
                ).also { it.markReady(tid, "card", "http://checkout1") }
            )

            Then("DataIntegrityViolationException이 발생한다 (uq_payments_pg_transaction_id 위반)") {
                shouldThrow<DataIntegrityViolationException> {
                    paymentJpaRepository.save(
                        Payment.create(
                            userId = 2L,
                            idempotencyKey = "pg-dup-key-02",
                            orderType = OrderType.BOOKING,
                            orderId = 101L,
                            method = PaymentMethod.CREDIT_CARD,
                            amount = BigDecimal("10000"),
                            currency = "KRW",
                        ).also { it.markReady(tid, "card", "http://checkout2") }
                    )
                }
            }
        }

        Given("두 트랜잭션이 같은 Payment row를 동시에 수정하면") {
            val savedPayment = transactionTemplate.execute {
                paymentJpaRepository.save(
                    Payment.create(
                        userId = 1L,
                        idempotencyKey = "optimistic-lock-key-01",
                        orderType = OrderType.BOOKING,
                        orderId = 200L,
                        method = PaymentMethod.CREDIT_CARD,
                        amount = BigDecimal("10000"),
                        currency = "KRW",
                    ).also {
                        it.markReady("MOCK_CARD_opt_lock_01", "card", "http://checkout")
                    }
                )
            } ?: error("Payment 저장 실패")

            val paymentId = savedPayment.id

            When("선행 트랜잭션이 먼저 커밋하면") {
                transactionTemplate.execute {
                    val payment = paymentJpaRepository.findById(paymentId).orElse(null)
                        ?: error("Payment not found: $paymentId")
                    payment.markCompleted(ZonedDateTime.now())
                    paymentJpaRepository.save(payment)
                }

                Then("후행 트랜잭션의 save는 ObjectOptimisticLockingFailureException을 던진다") {
                    shouldThrow<ObjectOptimisticLockingFailureException> {
                        transactionTemplate.execute {
                            // version=0인 stale 인스턴스를 merge하면 낙관락 충돌
                            savedPayment.markCompleted(ZonedDateTime.now())
                            paymentJpaRepository.saveAndFlush(savedPayment)
                        }
                    }
                }
            }
        }
    }
}
