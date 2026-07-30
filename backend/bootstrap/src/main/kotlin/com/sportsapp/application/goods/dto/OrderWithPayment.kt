package com.sportsapp.application.goods.dto

import com.sportsapp.domain.payment.entity.PaymentStatus
import java.math.BigDecimal

data class OrderWithPayment(
    val orderId: Long,
    val paymentId: Long?,
    val paymentStatus: PaymentStatus?,
    val totalAmount: BigDecimal,
)
