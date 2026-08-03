package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.InternalProgramCatalogItemResponse
import com.sportsapp.application.facility.dto.InternalProgramCatalogQuery
import com.sportsapp.domain.facility.service.ProgramDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계) 공급자
 * (S2-04, `GET /internal/catalog/programs`). [ProgramDomainService.searchForCatalog]는 MySQL
 * 소유 테이블(programs)만 읽어 MongoDB(facility 컨텍스트)에 접근하지 않는다.
 */
@Service
@Profile("!test-jpa")
class SearchProgramCatalogUseCase(
    private val programDomainService: ProgramDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(query: InternalProgramCatalogQuery): List<InternalProgramCatalogItemResponse> =
        programDomainService.searchForCatalog(query.keyword, query.toPageable())
            .content
            .map { InternalProgramCatalogItemResponse.of(it) }
}
