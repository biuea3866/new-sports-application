package com.sportsapp.domain.facility

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class FacilityNotOwnedByException(facilityId: String, userId: Long) : BusinessException(
    errorCode = "FACILITY_NOT_OWNED_BY",
    message = "Facility $facilityId is not owned by user $userId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
