package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 지역 백필 배치가 이미 실행 중일 때(분산 락 `lock:facility:region-backfill` 획득 실패) 던진다.
 */
class FacilityRegionBackfillInProgressException : BusinessException(
    errorCode = "FACILITY_REGION_BACKFILL_IN_PROGRESS",
    message = "Facility region backfill is already in progress. Please retry later.",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
