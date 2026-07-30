package com.sportsapp.domain.booking.exception

import com.sportsapp.domain.booking.entity.SlotStatus
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidSlotStatusException(
    from: SlotStatus,
    to: SlotStatus,
) : BusinessException(
    errorCode = "INVALID_SLOT_STATUS",
    message = "Cannot transit slot status from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
