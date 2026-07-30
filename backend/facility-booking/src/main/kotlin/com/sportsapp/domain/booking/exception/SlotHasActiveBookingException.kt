package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class SlotHasActiveBookingException(slotId: Long) : BusinessException(
    errorCode = "SLOT_HAS_ACTIVE_BOOKING",
    message = "Slot($slotId) has PENDING or CONFIRMED bookings and cannot be deleted",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
