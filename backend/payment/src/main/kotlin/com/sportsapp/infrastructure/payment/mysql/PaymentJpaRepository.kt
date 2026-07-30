package com.sportsapp.infrastructure.payment.mysql

import com.sportsapp.domain.payment.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentJpaRepository : JpaRepository<Payment, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByPgTransactionId(pgTransactionId: String): Payment?
    fun findAllByIdIn(ids: List<Long>): List<Payment>
}
