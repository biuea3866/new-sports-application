package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentRepository
import org.springframework.stereotype.Repository

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment {
        val entity = PaymentEntity.from(payment)
        return paymentJpaRepository.save(entity).toDomain()
    }

    override fun findById(id: Long): Payment? =
        paymentJpaRepository.findById(id).map { it.toDomain() }.orElse(null)

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentJpaRepository.findByIdempotencyKey(idempotencyKey).map { it.toDomain() }.orElse(null)
}
