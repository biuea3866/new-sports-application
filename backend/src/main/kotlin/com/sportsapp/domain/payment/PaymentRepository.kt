package com.sportsapp.domain.payment

interface PaymentRepository : CustomPaymentRepository {
    fun save(payment: Payment): Payment
    fun findById(id: Long): Payment?
    fun findByIdempotencyKey(idempotencyKey: String): Payment?
    fun findAllByIdIn(ids: List<Long>): List<Payment>
}
