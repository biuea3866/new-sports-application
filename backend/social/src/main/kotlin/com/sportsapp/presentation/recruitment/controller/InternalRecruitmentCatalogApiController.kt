package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.InternalRecruitmentCatalogItemResponse
import com.sportsapp.application.recruitment.usecase.SearchRecruitmentsForCatalogUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val DEFAULT_PAGE = "0"
private const val DEFAULT_SIZE = "20"

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 recruitment 원격 공급 엔드포인트 (S2-05, edge
 * `CatalogSearchGateway.searchRecruitments`의 2단계 구현 대상).
 *
 * **비로그인 공개 조회다** — 신원 헤더를 요구하지 않는다. 기존 `GET /recruitments`(permitAll)와
 * 동일한 비대칭이며, `/internal` 하위 전체 경로의 인가 규칙·호출자 인증은 S2-07 이 일괄 처리한다
 * (이 티켓은 경로와 응답만 정의한다).
 */
@RestController
@RequestMapping("/internal/catalog/recruitments")
class InternalRecruitmentCatalogApiController(
    private val searchRecruitmentsForCatalogUseCase: SearchRecruitmentsForCatalogUseCase,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = DEFAULT_PAGE) page: Int,
        @RequestParam(defaultValue = DEFAULT_SIZE) size: Int,
    ): ResponseEntity<List<InternalRecruitmentCatalogItemResponse>> =
        ResponseEntity.ok(searchRecruitmentsForCatalogUseCase.execute(keyword, page, size))
}
