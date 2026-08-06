package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.dto.InternalTicketingOrderHistoryItemResponse
import com.sportsapp.application.ticketing.usecase.FindTicketingOrderHistoryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 ticketing 원격 공급 엔드포인트 (S2-03, edge
 * `OrderHistoryGateway.findTicketingOrders` 의 2단계 구현 대상).
 *
 * 소비자 게이트웨이가 페이징을 받지 않아 이 경로도 페이지 파라미터를 두지 않는다 — 1단계 로컬
 * 어댑터와 동일한 형태다. 개인 데이터라 신원 헤더는 필수다.
 */
@RestController
@RequestMapping("/internal/order-history/ticketing")
class InternalTicketingOrderApiController(
    private val findTicketingOrderHistoryUseCase: FindTicketingOrderHistoryUseCase,
) {
    @GetMapping
    fun findTicketingOrders(
        @RequestHeader(INTERNAL_AUTH_SUBJECT_HEADER) subject: String,
    ): ResponseEntity<List<InternalTicketingOrderHistoryItemResponse>> {
        val userId = subject.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid internal identity header")
        return ResponseEntity.ok(findTicketingOrderHistoryUseCase.execute(userId))
    }
}
