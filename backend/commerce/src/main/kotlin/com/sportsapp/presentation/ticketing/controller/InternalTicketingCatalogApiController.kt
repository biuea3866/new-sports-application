package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogCriteria
import com.sportsapp.application.ticketing.dto.InternalTicketingCatalogItemResponse
import com.sportsapp.application.ticketing.usecase.SearchTicketingCatalogUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 ticketing 원격 공급 엔드포인트 (S2-03, edge
 * `CatalogSearchGateway.searchTicketingEvents` 의 2단계 구현 대상).
 *
 * **비로그인 공개 조회다** — 기존 `GET /events`(permitAll)와 동일한 비대칭이며, `/internal` 인가는
 * S2-07 이 일괄 처리한다.
 */
@RestController
@RequestMapping("/internal/catalog/ticketing")
class InternalTicketingCatalogApiController(
    private val searchTicketingCatalogUseCase: SearchTicketingCatalogUseCase,
) {
    @GetMapping
    fun searchTicketingEvents(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = InternalTicketingCatalogCriteria.DEFAULT_PAGE_PARAM) page: Int,
        @RequestParam(defaultValue = InternalTicketingCatalogCriteria.DEFAULT_SIZE_PARAM) size: Int,
    ): ResponseEntity<List<InternalTicketingCatalogItemResponse>> =
        ResponseEntity.ok(
            searchTicketingCatalogUseCase.execute(
                InternalTicketingCatalogCriteria(keyword = keyword, page = page, size = size),
            ),
        )
}
