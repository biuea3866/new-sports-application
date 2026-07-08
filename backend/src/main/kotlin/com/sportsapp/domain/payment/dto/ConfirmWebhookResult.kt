package com.sportsapp.domain.payment.dto

import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import java.math.BigDecimal
import java.time.ZonedDateTime

data class ConfirmWebhookResult(
    val id: Long,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val currency: String,
    val status: PaymentStatus,
    val pgTransactionId: String?,
    val checkoutUrl: String?,
    val paidAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(payment: Payment): ConfirmWebhookResult = ConfirmWebhookResult(
            id = payment.id,
            orderType = payment.orderType,
            orderId = payment.orderId,
            method = payment.method,
            amount = payment.amount,
            currency = payment.currency,
            status = payment.status,
            pgTransactionId = payment.pgTransactionId,
            checkoutUrl = payment.checkoutUrl,
            paidAt = payment.paidAt,
            createdAt = payment.createdAt,
        )
    }
}
