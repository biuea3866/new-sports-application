package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 취소 이벤트를 받아 자기 PENDING 티켓 주문을 취소하고 좌석/티켓을 정리한다.
 * cancelOrder 는 이미 CANCELLED 인 주문을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class CancelTicketingPaymentUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional
    fun execute(orderId: Long) {
        ticketingDomainService.cancelOrder(orderId)
    }
}
