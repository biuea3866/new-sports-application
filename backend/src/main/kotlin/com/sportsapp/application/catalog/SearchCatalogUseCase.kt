package com.sportsapp.application.catalog

import com.sportsapp.application.catalog.dto.CatalogSearchCriteria
import com.sportsapp.application.catalog.dto.CatalogSearchResponse
import org.springframework.stereotype.Service

/**
 * catalog 통합검색 진입점 (BE-07). 실제 조합·병렬 fan-out은 [CatalogCompositionService]가
 * 수행한다 — 읽기 전용 조합이라 트랜잭션이 불필요하다(각 도메인 조회가 별도 executor 스레드에서
 * 실행되므로 UseCase 스레드의 트랜잭션이 전파되지 않는다).
 */
@Service
class SearchCatalogUseCase(
    private val catalogCompositionService: CatalogCompositionService,
) {
    fun execute(criteria: CatalogSearchCriteria): CatalogSearchResponse =
        catalogCompositionService.search(criteria)
}
