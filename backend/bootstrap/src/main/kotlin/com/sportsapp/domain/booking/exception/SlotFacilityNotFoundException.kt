package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SlotFacilityNotFoundException(facilityId: String) : BusinessException(
    errorCode = "SLOT_FACILITY_NOT_FOUND",
    message = "Facility($facilityId) referenced by the slot does not exist",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
