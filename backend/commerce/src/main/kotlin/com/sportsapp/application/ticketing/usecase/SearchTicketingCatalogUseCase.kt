package com.sportsapp.application.ticketing.usecase

import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogCriteria
import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogItemResponse
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchTicketingEvents` 원격 구현(2단계)
 * 공급자 (S2-03, `GET /internal/catalog/ticketing`).
 *
 * 최저 좌석가 조합은 [TicketingDomainService.searchOpenEventsForCatalog] 가 이미 수행한다 —
 * 여기서 다시 계산하지 않는다. 여러 도메인 병합·정렬은 edge 파사드의 책임이다.
 */
@Service
class SearchTicketingCatalogUseCase(
    private val ticketingDomainService: TicketingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: InternalTicketingCatalogCriteria): List<InternalTicketingCatalogItemResponse> =
        ticketingDomainService.searchOpenEventsForCatalog(criteria.keyword, criteria.toPageable())
            .content
            .map(InternalTicketingCatalogItemResponse::of)
}
