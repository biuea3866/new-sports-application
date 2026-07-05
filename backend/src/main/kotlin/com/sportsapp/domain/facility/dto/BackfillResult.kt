package com.sportsapp.domain.facility.dto

/**
 * [com.sportsapp.domain.facility.service.FacilityRegionBackfillService] 실행 결과.
 *
 * @param updated 이번 실행에서 재해석·저장된 문서 수 (UNSPECIFIED로 보존된 문서 포함)
 * @param unspecified 그중 [com.sportsapp.domain.facility.vo.FacilityRegion.UNSPECIFIED]로 보존된 문서 수
 */
data class BackfillResult(
    val updated: Int,
    val unspecified: Int,
)
