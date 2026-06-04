package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SlotBusyException(slotId: Long) : BusinessException(
    errorCode = "SLOT_BUSY",
    message = "Slot $slotId is currently locked by another request. Please try again.",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
