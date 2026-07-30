package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidTicketStateException(message: String) : BusinessException(
    errorCode = "INVALID_TICKET_STATE",
    message = message,
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
