package com.sportsapp.domain.payment

interface PaymentRepository : PaymentCustomRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findByPgTransactionId(pgTransactionId: String): Payment?
    fun findAllByIdIn(ids: List<Long>): List<Payment>
}
