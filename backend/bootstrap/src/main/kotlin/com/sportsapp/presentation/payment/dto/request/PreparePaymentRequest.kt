package com.sportsapp.presentation.payment.dto.request

import com.sportsapp.application.payment.dto.PreparePaymentCommand
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal

data class PreparePaymentRequest(
    @field:NotNull val orderType: OrderType,
    @field:NotNull val orderId: Long,
    @field:NotNull val method: PaymentMethod,
    @field:NotNull @field:DecimalMin(value = "0.01", message = "amount must be positive") val amount: BigDecimal,
    @field:NotBlank val currency: String,
    @field:NotBlank val itemName: String,
    @field:NotBlank val returnUrl: String,
    @field:NotBlank val failUrl: String,
) {
    fun toCommand(userId: Long, idempotencyKey: String): PreparePaymentCommand = PreparePaymentCommand(
        userId = userId,
        idempotencyKey = idempotencyKey,
        orderType = orderType,
        orderId = orderId,
        method = method,
        amount = amount,
        currency = currency,
        itemName = itemName,
        returnUrl = returnUrl,
        failUrl = failUrl,
    )
}
