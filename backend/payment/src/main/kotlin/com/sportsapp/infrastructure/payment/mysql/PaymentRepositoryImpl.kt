package com.sportsapp.infrastructure.payment.mysql

import com.sportsapp.domain.payment.repository.PaymentCustomRepository
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.ZonedDateTime

@Repository
class PaymentRepositoryImpl(
    private val paymentJpaRepository: PaymentJpaRepository,
    private val paymentCustomRepository: PaymentCustomRepository,
) : PaymentRepository {

    override fun save(payment: Payment): Payment = paymentJpaRepository.save(payment)

    override fun findById(id: Long): Payment? = paymentJpaRepository.findByIdOrNull(id)

    override fun findByIdempotencyKey(idempotencyKey: String): Payment? =
        paymentJpaRepository.findByIdempotencyKey(idempotencyKey)

    override fun findByPgTransactionId(pgTransactionId: String): Payment? =
        paymentJpaRepository.findByPgTransactionId(pgTransactionId)

    override fun findAllByIdIn(ids: List<Long>): List<Payment> =
        paymentJpaRepository.findAllByIdIn(ids)

    override fun findByUserIdAndConditions(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> = paymentCustomRepository.findByUserIdAndConditions(
        userId = userId,
        status = status,
        paidAtFrom = paidAtFrom,
        paidAtTo = paidAtTo,
        pageable = pageable,
    )
}
