package com.sportsapp.presentation.facility.controller

import com.sportsapp.application.facility.dto.InternalProgramCatalogCriteria
import com.sportsapp.application.facility.dto.InternalProgramCatalogItemResponse
import com.sportsapp.application.facility.usecase.SearchProgramCatalogUseCase
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계)이 호출할
 * 공급자 엔드포인트 (S2-04). internal 경로 전체(`/internal/` 이하) 호출자 인가·인증은 S2-07(wave 2)이
 * 일괄 처리하므로 이 컨트롤러는 조회 로직만 다룬다.
 */
@RestController
@RequestMapping("/internal/catalog/programs")
@Profile("!test-jpa")
class InternalProgramCatalogApiController(
    private val searchProgramCatalogUseCase: SearchProgramCatalogUseCase,
) {

    @GetMapping
    fun searchPrograms(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = InternalProgramCatalogCriteria.DEFAULT_PAGE_PARAM) page: Int,
        @RequestParam(defaultValue = InternalProgramCatalogCriteria.DEFAULT_SIZE_PARAM) size: Int,
    ): ResponseEntity<List<InternalProgramCatalogItemResponse>> {
        val criteria = InternalProgramCatalogCriteria(keyword = keyword, page = page, size = size)
        return ResponseEntity.ok(searchProgramCatalogUseCase.execute(criteria))
    }
}
