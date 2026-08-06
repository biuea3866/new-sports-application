package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryCriteria
import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryItemResponse
import com.sportsapp.application.goods.usecase.FindGoodsOrderHistoryUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 goods 원격 공급 엔드포인트 (S2-03, edge
 * `OrderHistoryGateway.findGoodsOrders` 의 2단계 구현 대상).
 *
 * 개인 데이터라 사용자 식별은 W1-06b 내부 신원 전파 계약의 [INTERNAL_AUTH_SUBJECT_HEADER] 헤더로
 * 받는다 — 헤더가 없으면 `MissingRequestHeaderException`, 값이 사용자 PK 로 해석되지 않으면
 * `IllegalArgumentException` 으로 각각 400 이 된다. `/internal` 경로의 호출자 인증·인가는 S2-07 이
 * 일괄 처리한다.
 */
@RestController
@RequestMapping("/internal/order-history/goods")
class InternalGoodsOrderApiController(
    private val findGoodsOrderHistoryUseCase: FindGoodsOrderHistoryUseCase,
) {
    @GetMapping
    fun findGoodsOrders(
        @RequestHeader(INTERNAL_AUTH_SUBJECT_HEADER) subject: String,
        @RequestParam(defaultValue = InternalGoodsOrderHistoryCriteria.DEFAULT_PAGE_PARAM) page: Int,
        @RequestParam(defaultValue = InternalGoodsOrderHistoryCriteria.DEFAULT_SIZE_PARAM) size: Int,
    ): ResponseEntity<List<InternalGoodsOrderHistoryItemResponse>> {
        val userId = subject.toLongOrNull()
            // GlobalExceptionHandler 가 message 를 응답 detail 로 그대로 내보내므로, 입력값·헤더
            // 이름을 담지 않는다 (내부 전용 경로라도 받은 값을 되돌려줄 이유가 없다).
            ?: throw IllegalArgumentException("Invalid internal identity header")
        return ResponseEntity.ok(
            findGoodsOrderHistoryUseCase.execute(
                InternalGoodsOrderHistoryCriteria(userId = userId, page = page, size = size),
            ),
        )
    }
}
