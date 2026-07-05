package com.sportsapp.application.facility.usecase

import com.sportsapp.domain.facility.dto.BackfillResult
import com.sportsapp.domain.facility.service.FacilityRegionBackfillService
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

/**
 * [FacilityRegionBackfillService]는 페이지 단위 문서별 save로 처리되어 장시간 트랜잭션을
 * 만들지 않는다(TDD "실패 경로·동시성·멱등" 참조) — 이 UseCase는 그 설계에 맞춰
 * 의도적으로 `@Transactional`을 선언하지 않는다.
 */
@Service
@Profile("!test-jpa")
class BackfillFacilityRegionUseCase(
    private val facilityRegionBackfillService: FacilityRegionBackfillService,
) {
    fun execute(pageSize: Int): BackfillResult =
        facilityRegionBackfillService.backfill(pageSize)
}
