package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class FacilityNotFoundException(id: String) : BusinessException(
    errorCode = "FACILITY_NOT_FOUND",
    message = "Facility with id $id not found",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
