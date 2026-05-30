package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.domain.payment.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByPgTransactionId(pgTransactionId: String): Payment?
    fun findAllByIdIn(ids: List<Long>): List<Payment>
}
