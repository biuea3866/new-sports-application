package com.sportsapp.application.ticketing.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchTicketingEvents` 원격 구현(2단계)이
 * 호출하는 공급자 엔드포인트(`GET /internal/catalog/ticketing`) 조회 조건 (S2-03).
 *
 * `size` 상한을 **걸지 않는다** — 상한은 소비자(파사드)가 창을 계산하기 전에 이미 적용했고, 공급자가
 * 다시 자르면 page >= 1 결과가 유실돼 섀도 응답 동일성(S2-06·S2-15)이 깨진다. 상세 근거는 형제
 * 공급자 `InternalGoodsCatalogCriteria` KDoc 과 동일하며, 이 결정은 전용 테스트가 잠근다.
 * 창의 deep page 폭증은 소비자 쪽 부채다(후속 리스크 등록부 R-28).
 */
data class InternalTicketingCatalogCriteria(
    val keyword: String?,
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
