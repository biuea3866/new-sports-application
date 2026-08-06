package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.InternalGoodsCatalogCriteria
import com.sportsapp.application.goods.dto.InternalGoodsCatalogItemResponse
import com.sportsapp.application.goods.usecase.SearchGoodsCatalogUseCase
import com.sportsapp.domain.goods.vo.SellerType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 goods 원격 공급 엔드포인트 (S2-03, edge
 * `CatalogSearchGateway.searchGoods` 의 2단계 구현 대상).
 *
 * **비로그인 공개 조회다** — 신원 헤더를 요구하지 않는다. 기존 `GET /products`(permitAll)와 동일한
 * 비대칭이며, `/internal` 하위 전체 경로의 인가 규칙·호출자 인증은 S2-07 이 일괄 처리한다
 * (이 티켓은 경로와 응답만 정의한다).
 */
@RestController
@RequestMapping("/internal/catalog/goods")
class InternalGoodsCatalogApiController(
    private val searchGoodsCatalogUseCase: SearchGoodsCatalogUseCase,
) {
    @GetMapping
    fun searchGoods(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) sellerType: SellerType?,
        @RequestParam(defaultValue = InternalGoodsCatalogCriteria.DEFAULT_PAGE_PARAM) page: Int,
        @RequestParam(defaultValue = InternalGoodsCatalogCriteria.DEFAULT_SIZE_PARAM) size: Int,
    ): ResponseEntity<List<InternalGoodsCatalogItemResponse>> =
        ResponseEntity.ok(
            searchGoodsCatalogUseCase.execute(
                InternalGoodsCatalogCriteria(keyword = keyword, sellerType = sellerType, page = page, size = size),
            ),
        )
}
