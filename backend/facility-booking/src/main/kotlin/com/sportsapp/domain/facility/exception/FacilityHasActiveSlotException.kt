package com.sportsapp.domain.facility.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class FacilityHasActiveSlotException(facilityId: String) : BusinessException(
    errorCode = "FACILITY_HAS_ACTIVE_SLOT",
    message = "Facility $facilityId has active slots and cannot be deleted",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
