package com.sportsapp.infrastructure.persistence.payment

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface PaymentJpaRepository : JpaRepository<PaymentEntity, Long> {
    fun findByIdempotencyKey(idempotencyKey: String): Optional<PaymentEntity>
}
