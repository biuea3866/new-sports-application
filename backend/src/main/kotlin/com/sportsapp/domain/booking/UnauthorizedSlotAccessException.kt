package com.sportsapp.domain.booking

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedSlotAccessException(slotId: Long) : BusinessException(
    errorCode = "UNAUTHORIZED_SLOT_ACCESS",
    message = "Slot($slotId) is not owned by the requester",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
