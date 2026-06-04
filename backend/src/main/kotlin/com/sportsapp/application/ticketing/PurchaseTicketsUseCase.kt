package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PgInitiateCommand
import com.sportsapp.domain.ticketing.TicketingDomainService
import org.springframework.stereotype.Service

@Service
class PurchaseTicketsUseCase(
    private val ticketingDomainService: TicketingDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    /**
     * 1단계(tx): 좌석 락 검증 + 주문 생성 + 결제 PENDING 저장
     * 2단계(no-tx): PG 호출 — 외부 IO를 트랜잭션 밖으로 분리
     */
    fun execute(command: PurchaseTicketsCommand): TicketOrderResponse {
        ticketingDomainService.verifyLockOwner(command.lockId, command.userId)
        val totalAmount = ticketingDomainService.calculateAmount(command.lockId)
        val orderResult = ticketingDomainService.createPendingOrder(command.lockId, command.userId)
        val paymentId = paymentDomainService.createPending(
            userId = command.userId,
            idempotencyKey = command.idempotencyKey,
            orderType = OrderType.TICKETING,
            orderId = orderResult.ticketOrderId,
            method = command.method,
            amount = totalAmount,
            currency = command.currency,
        )
        paymentDomainService.initiatePg(
            PgInitiateCommand(
                paymentId = paymentId,
                method = command.method,
                idempotencyKey = command.idempotencyKey,
                userId = command.userId,
                orderType = OrderType.TICKETING,
                orderId = orderResult.ticketOrderId,
                amount = totalAmount,
                currency = command.currency,
                itemName = "${OrderType.TICKETING} #${orderResult.ticketOrderId}",
                returnUrl = "",
                failUrl = "",
            )
        )
        return TicketOrderResponse(
            ticketOrderId = orderResult.ticketOrderId,
            status = orderResult.status,
        )
    }
}
