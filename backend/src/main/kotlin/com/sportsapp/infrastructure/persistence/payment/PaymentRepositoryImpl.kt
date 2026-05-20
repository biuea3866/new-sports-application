package com.sportsapp.infrastructure.persistence.payment

import com.sportsapp.domain.payment.CustomPaymentRepository
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentRepository
import com.sportsapp.domain.payment.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val customPaymentRepository: CustomPaymentRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun findById(id: Long): Payment? = paymentJpaRepository.findByIdOrNull(id)

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentJpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findAllByIdIn(ids: List<Long>): List<Payment> =
        paymentJpaRepository.findAllByIdIn(ids)

    override fun findByUserIdAndConditions(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> = customPaymentRepository.findByUserIdAndConditions(
        userId = userId,
        status = status,
        paidAtFrom = paidAtFrom,
        paidAtTo = paidAtTo,
        pageable = pageable,
    )
}
