package com.sportsapp.application.facility.usecase

import com.sportsapp.application.facility.dto.InternalProgramCatalogItemResponse
import com.sportsapp.application.facility.dto.InternalProgramCatalogQuery
import com.sportsapp.domain.facility.service.ProgramDomainService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * edge catalog 통합검색(BE-07)의 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계) 공급자
 * (S2-04, `GET /internal/catalog/programs`).
 *
 * 시설상품 자체는 MySQL 소유 테이블(programs)에서 읽고, 구분에 필요한 시설명은
 * [ProgramDomainService.findFacilityNamesBy]로 facilityId 를 모아 **한 번만** 배치 조회한다
 * (N+1 방지). 두 조회 모두 같은 서비스(facility-booking) 안이라 컨텍스트 경계를 넘지 않으며,
 * MongoDB 저장소를 이 UseCase 가 직접 주입받지 않는다(DomainService 경유).
 */
@Service
@Profile("!test-jpa")
class SearchProgramCatalogUseCase(
    private val programDomainService: ProgramDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(query: InternalProgramCatalogQuery): List<InternalProgramCatalogItemResponse> {
        val programs = programDomainService.searchForCatalog(query.keyword, query.toPageable()).content
        val facilityNameByFacilityId = programDomainService.findFacilityNamesBy(programs.map { it.facilityId })
        return programs.map { InternalProgramCatalogItemResponse.of(it, facilityNameByFacilityId[it.facilityId]) }
    }
}
