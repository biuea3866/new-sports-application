package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun findById(id: Long): Payment? = paymentJpaRepository.findByIdOrNull(id)

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentJpaRepository.findByIdempotencyKey(idempotencyKey)
}
