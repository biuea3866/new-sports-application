package com.sportsapp.presentation.catalog

import com.sportsapp.application.catalog.SearchCatalogUseCase
import com.sportsapp.application.catalog.dto.CatalogItemType
import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import com.sportsapp.domain.goods.vo.SellerType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * catalog 통합검색 REST 계약 (BE-07). permitAll matcher는 BE-09가 SecurityConfig에 등록한다
 * (Single Writer — 이 파일은 SecurityConfig를 건드리지 않는다).
 */
@RestController
@RequestMapping("/api/catalog")
class CatalogApiController(
    private val searchCatalogUseCase: SearchCatalogUseCase,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) itemType: CatalogItemType?,
        @RequestParam(required = false) sellerType: SellerType?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CatalogSearchResponse> {
        val criteria = CatalogSearchCriteria(
            keyword = keyword,
            itemType = itemType,
            sellerType = sellerType,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(searchCatalogUseCase.execute(criteria))
    }
}
