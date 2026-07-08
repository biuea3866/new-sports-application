package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedFacilityAccessException(facilityId: String) : BusinessException(
    errorCode = "UNAUTHORIZED_FACILITY_ACCESS",
    message = "Facility($facilityId) is not owned by the requester",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
