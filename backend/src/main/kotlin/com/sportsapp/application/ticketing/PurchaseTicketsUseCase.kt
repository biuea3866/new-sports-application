package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PurchaseTicketsUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional
    fun execute(command: PurchaseTicketsCommand): TicketOrderResponse {
        ticketingDomainService.verifyLockOwner(command.lockId, command.userId)
        val totalAmount = ticketingDomainService.calculateAmount(command.lockId)
        val ticketOrder = ticketingDomainService.createPendingOrder(command.lockId, command.userId)
        paymentDomainService.create(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = OrderType.TICKETING,
            orderId = ticketOrder.id,
            method = command.method,
            amount = totalAmount,
            currency = command.currency,
        )
        return TicketOrderResponse.of(ticketOrder)
    }
}
