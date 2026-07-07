package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SlotClosedException(slotId: Long) : BusinessException(
    errorCode = "SLOT_CLOSED",
    message = "Slot($slotId) is closed for new bookings",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
