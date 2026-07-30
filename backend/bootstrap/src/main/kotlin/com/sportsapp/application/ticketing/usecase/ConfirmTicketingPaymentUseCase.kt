package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 확정 이벤트를 받아 자기 티켓 주문을 CONFIRMED 로 전이한다.
 * confirmOrder 는 이미 CONFIRMED 인 주문을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class ConfirmTicketingPaymentUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(orderId: Long, paymentId: Long) {
        ticketingDomainService.confirmOrder(orderId, paymentId)
    }
}
