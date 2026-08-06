package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.InternalTicketingOrderHistoryItemResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 주문내역(BE-08)의 `OrderHistoryGateway.findTicketingOrders` 원격 구현(2단계) 공급자
 * (S2-03, `GET /internal/order-history/ticketing`).
 *
 * 소비자 게이트웨이 시그니처가 페이징을 받지 않아(`findTicketingOrders(userId)`) 여기서도 전량
 * 반환한다 — 1단계 로컬 어댑터와 동일한 형태다. `userId` 소유 주문만 반환하며 소유권 경계는
 * [TicketingDomainService.listTicketOrdersBy] 조회가 보장한다.
 */
@Service
class FindTicketingOrderHistoryUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<InternalTicketingOrderHistoryItemResponse> =
        ticketingDomainService.listTicketOrdersBy(userId)
            .map(InternalTicketingOrderHistoryItemResponse::of)
}
