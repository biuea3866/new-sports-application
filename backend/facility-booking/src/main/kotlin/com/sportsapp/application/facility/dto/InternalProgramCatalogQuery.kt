package com.sportsapp.application.facility.dto

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계)이 호출하는
 * 공급자 엔드포인트(`GET /internal/catalog/programs`) 조회 조건 (S2-04).
 *
 * 정렬은 [com.sportsapp.domain.facility.repository.ProgramCustomRepository.searchForCatalog] 구현체가
 * createdAt desc로 고정하므로 여기서는 offset·limit만 구성한다.
 */
data class InternalProgramCatalogQuery(
    val keyword: String?,
    val page: Int,
    val size: Int,
) {
    fun toPageable(): Pageable = PageRequest.of(page, size)

    companion object {
        const val DEFAULT_PAGE = 0
        const val DEFAULT_SIZE = 20
    }
}
