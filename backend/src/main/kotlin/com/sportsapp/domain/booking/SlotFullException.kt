package com.sportsapp.domain.booking

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SlotFullException(slotId: Long) : BusinessException(
    errorCode = "SLOT_FULL",
    message = "Slot $slotId has no remaining capacity.",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
