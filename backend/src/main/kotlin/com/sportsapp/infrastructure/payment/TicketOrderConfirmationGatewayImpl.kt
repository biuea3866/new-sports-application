package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.OrderConfirmationGateway
import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component

@Primary
@Component
class TicketOrderConfirmationGatewayImpl(
    private val ticketingDomainService: TicketingDomainService,
) : OrderConfirmationGateway {

    override fun confirm(orderType: OrderType, orderId: Long, paymentId: Long) {
        if (orderType != OrderType.TICKETING) return
        ticketingDomainService.confirmOrder(orderId, paymentId)
    }
}
