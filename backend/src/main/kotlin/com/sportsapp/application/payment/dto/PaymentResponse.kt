package com.sportsapp.application.payment.dto

import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import java.math.BigDecimal
import java.time.ZonedDateTime

data class PaymentResponse(
    val id: Long,
    val orderType: OrderType,
    val orderId: Long,
    val method: PaymentMethod,
    val amount: BigDecimal,
    val status: PaymentStatus,
    val createdAt: ZonedDateTime,
    val paidAt: ZonedDateTime?,
    val checkoutUrl: String?,
) {
    companion object {
        fun of(payment: Payment): PaymentResponse = PaymentResponse(
            id = payment.id,
            orderType = payment.orderType,
            orderId = payment.orderId,
            method = payment.method,
            amount = payment.amount,
            status = payment.status,
            createdAt = payment.createdAt,
            paidAt = payment.paidAt,
            checkoutUrl = payment.checkoutUrl,
        )

        fun of(result: ConfirmWebhookResult): PaymentResponse = PaymentResponse(
            id = result.id,
            orderType = result.orderType,
            orderId = result.orderId,
            method = result.method,
            amount = result.amount,
            status = result.status,
            createdAt = result.createdAt,
            paidAt = result.paidAt,
            checkoutUrl = result.checkoutUrl,
        )
    }
}
