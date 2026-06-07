package com.sportsapp.domain.payment.repository

import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

interface PaymentCustomRepository {
    fun findByUserIdAndConditions(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment>
}
