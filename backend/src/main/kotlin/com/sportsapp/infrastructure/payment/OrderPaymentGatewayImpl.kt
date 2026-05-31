package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderPaymentGateway
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import java.math.BigDecimal
import org.springframework.stereotype.Component

@Component
class OrderPaymentGatewayImpl(
    private val paymentDomainService: PaymentDomainService,
) : OrderPaymentGateway {

    override fun createPayment(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Payment = paymentDomainService.create(
        userId = userId,
        idempotencyKey = idempotencyKey,
        orderType = orderType,
        orderId = orderId,
        method = method,
        amount = amount,
        currency = currency,
    )
}
