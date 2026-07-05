package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedFacilityAccessException(facilityId: String, userId: Long) : BusinessException(
    errorCode = "UNAUTHORIZED_FACILITY_ACCESS",
    message = "User($userId) is not the owner of facility($facilityId)",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
