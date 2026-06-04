package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.gateway.OrderConfirmationGateway
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Component

@Component
class OrderConfirmationGatewayImpl(
    private val bookingDomainService: BookingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val ticketingDomainService: TicketingDomainService,
) : OrderConfirmationGateway {

    override fun confirm(orderType: OrderType, orderId: Long, paymentId: Long) {
        when (orderType) {
            OrderType.BOOKING -> bookingDomainService.confirmBooking(orderId, paymentId)
            OrderType.GOODS -> goodsDomainService.markPaid(orderId, paymentId)
            OrderType.TICKETING -> ticketingDomainService.confirmOrder(orderId, paymentId)
        }
    }

    override fun cancel(orderType: OrderType, orderId: Long, paymentId: Long) {
        when (orderType) {
            OrderType.BOOKING -> bookingDomainService.cancelPending(orderId)
            OrderType.GOODS -> goodsDomainService.cancelPendingOrder(orderId)
            OrderType.TICKETING -> ticketingDomainService.cancelOrder(orderId)
        }
    }
}
