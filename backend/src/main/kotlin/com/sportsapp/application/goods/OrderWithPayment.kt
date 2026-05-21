package com.sportsapp.application.goods

import com.sportsapp.domain.payment.PaymentStatus
import java.math.BigDecimal

data class OrderWithPayment(
    val orderId: Long,
    val paymentId: Long,
    val paymentStatus: PaymentStatus,
    val totalAmount: BigDecimal,
)
