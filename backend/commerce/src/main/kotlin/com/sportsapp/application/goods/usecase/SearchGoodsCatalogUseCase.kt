package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.InternalGoodsCatalogCriteria
import com.sportsapp.application.goods.dto.InternalGoodsCatalogItemResponse
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchGoods` 원격 구현(2단계) 공급자
 * (S2-03, `GET /internal/catalog/goods`).
 *
 * 파사드가 쓰지 않는 검색 조건(카테고리·가격 범위)은 null 로 전달한다 — 1단계 로컬 어댑터와 동일한
 * 호출 형태를 유지해 섀도 응답 동일성이 성립하게 한다. `size` 는 절삭 없이 도메인 페이징에
 * 위임한다(근거는 [InternalGoodsCatalogCriteria] KDoc).
 */
@Service
class SearchGoodsCatalogUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: InternalGoodsCatalogCriteria): List<InternalGoodsCatalogItemResponse> =
        goodsDomainService.search(
            category = null,
            keyword = criteria.keyword,
            priceMin = null,
            priceMax = null,
            sellerType = criteria.sellerType,
            pageable = criteria.toPageable(),
        ).content.map(InternalGoodsCatalogItemResponse::of)
}
