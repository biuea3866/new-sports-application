package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service

@Service
class PurchaseTicketsUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    fun execute(command: PurchaseTicketsCommand): TicketOrderResponse {
        ticketingDomainService.verifyLockOwner(command.lockId, command.userId)
        val totalAmount = ticketingDomainService.calculateAmount(command.lockId)
        val ticketOrder = ticketingDomainService.createPendingOrder(command.lockId, command.userId)

        val payment = runCatching {
            paymentDomainService.create(
                userId = command.userId,
                idempotencyKey = command.idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = ticketOrder.id,
                method = command.method,
                amount = totalAmount,
                currency = command.currency,
            )
        }.getOrElse {
            ticketingDomainService.cancelOrder(ticketOrder.id)
            return TicketOrderResponse.of(ticketOrder)
        }
        if (payment.status == PaymentStatus.FAILED) {
            ticketingDomainService.cancelOrder(ticketOrder.id)
        }
        return TicketOrderResponse.of(ticketOrder)
    }
}
