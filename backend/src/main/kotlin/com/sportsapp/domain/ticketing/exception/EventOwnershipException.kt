package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class EventOwnershipException(eventId: Long) : BusinessException(
    errorCode = "EVENT_NOT_FOUND",
    message = "Event not found: $eventId"
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
