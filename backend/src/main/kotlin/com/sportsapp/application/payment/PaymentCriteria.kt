package com.sportsapp.application.payment

import com.sportsapp.domain.payment.PaymentStatus
import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

data class PaymentCriteria(
    val userId: Long,
    val status: PaymentStatus?,
    val paidAtFrom: ZonedDateTime?,
    val paidAtTo: ZonedDateTime?,
    val pageable: Pageable,
)
