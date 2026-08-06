package com.sportsapp.application.goods.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * 통합 주문내역(BE-08)의 `OrderHistoryGateway.findGoodsOrders` 원격 구현(2단계)이 호출하는 공급자
 * 엔드포인트(`GET /internal/order-history/goods`) 조회 조건 (S2-03).
 *
 * `page`·`size` 를 개별 `Int` 인자로 넘기면 호출부에서 위치가 뒤바뀌어도 컴파일이 통과해 조용히
 * 오동작한다 — 인접한 동일 타입 인자를 값 객체로 묶어 그 경로를 없앤다.
 *
 * `size` 상한은 **걸지 않는다** — 파사드가 `PageRequest.of(0, windowSize)` 로 창을 요청해 자기가
 * 잘라내므로 공급자 절삭은 결과를 유실시킨다(근거는 `InternalGoodsCatalogCriteria` KDoc, 부채는
 * 후속 리스크 등록부 R-28).
 */
data class InternalGoodsOrderHistoryCriteria(
    val userId: Long,
    val page: Int,
    val size: Int,
) {
    fun toPageable(): Pageable = PageRequest.of(page, size)

    companion object {
        /** `@RequestParam(defaultValue = ..)` 는 컴파일 상수만 받으므로 문자열로 둔다. */
        const val DEFAULT_PAGE_PARAM = "0"
        const val DEFAULT_SIZE_PARAM = "20"
    }
}
